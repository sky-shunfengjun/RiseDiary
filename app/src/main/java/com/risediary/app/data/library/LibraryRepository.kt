package com.risediary.app.data.library

import android.util.AtomicFile
import androidx.room.withTransaction
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceGate
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LibraryAssetIndex {
    suspend fun asset(id: String): LibraryAsset?
    suspend fun byHash(hash: String): LibraryAsset?
    suspend fun names(folderId: String?): Set<String>
    suspend fun location(asset: LibraryAsset): String = asset.name
    suspend fun publish(asset: LibraryAsset)
}

@Singleton
class LibraryRepository internal constructor(private val database: AppDatabase, private val storage: LibraryStorage,
    private val gate: DataMaintenanceGate, private val usage: LibraryUsageRegistry,
    private val restoreUsage: suspend () -> Unit = {}) : LibraryAssetIndex {
    @Inject constructor(database: AppDatabase, storage: LibraryStorage, gate: DataMaintenanceGate,
        usage: LibraryUsageRegistry, timerGuard: LibraryTimerGuard) : this(database, storage, gate, usage, timerGuard::restore)
    private val dao = database.libraryDao()
    private val catalogMutex = Mutex()
    private val cleanupMutex = Mutex()
    private val coverMutex = LibraryCoverFileAccess.mutex
    private val coverVersion = MutableStateFlow(0L)
    val coverRevision = coverVersion.asStateFlow()
    private val catalogProblem = MutableStateFlow<String?>(null)
    val catalogError = catalogProblem.asStateFlow()
    val snapshots = combine(dao.observeAllAssets(), dao.observeAllFolders()) { assets, folders -> LibrarySnapshot(assets, folders) }
    suspend fun snapshot(): LibrarySnapshot = database.withTransaction { LibrarySnapshot(dao.allAssets(), dao.allFolders()) }
    suspend fun createFolder(parent: String?, name: String) = change { it.createFolder(parent, name, UUID.randomUUID().toString(), System.currentTimeMillis()) }
    suspend fun rename(key: String, name: String) = change { it.rename(key, name) }
    suspend fun move(keys: Set<String>, target: String?) = change { it.move(keys, target) }
    private suspend fun change(transform: (LibraryTree) -> LibrarySnapshot) {
        gate.write {
            database.withTransaction {
                val before = snapshot()
                val after = transform(LibraryTree(before))
                val oldAssets = before.assets.associateBy { it.id }; val oldFolders = before.folders.associateBy { it.id }
                dao.updateAssets(after.assets.filter { it != oldAssets[it.id] })
                after.folders.filter { it.id !in oldFolders }.forEach { dao.insertFolder(it) }
                dao.updateFolders(after.folders.filter { it.id in oldFolders && it != oldFolders[it.id] })
            }
        }
        refreshCatalogAfterChange()
    }
    suspend fun previewDelete(keys: Set<String>): LibraryDeletePreview = database.withTransaction {
        val selection = LibraryTree(snapshot()).selection(keys)
        val records = selection.assetIds.toList().chunked(400).sumOf { database.flightDao().countMaterialLinks(it) }
        LibraryDeletePreview(selection.folderIds.size, selection.assetIds.size, records, selection.folderIds, selection.assetIds)
    }
    suspend fun delete(keys: Set<String>, expected: LibraryDeletePreview? = null): Int {
        restoreUsage()
        var reservation: AutoCloseable? = null
        try { gate.write {
            database.withTransaction {
                val before = snapshot()
                val selected = LibraryTree(before).selection(keys)
                if (expected != null) {
                    val records = selected.assetIds.toList().chunked(400).sumOf { database.flightDao().countMaterialLinks(it) }
                    require(expected == LibraryDeletePreview(selected.folderIds.size, selected.assetIds.size, records, selected.folderIds, selected.assetIds)) {
                        "内容已有变化，请关闭确认框后重新选择，核对新的删除范围"
                    }
                }
                reservation = usage.reserveDeletion(selected.assetIds)
                dao.updateAssets(before.assets.filter { it.id in selected.assetIds }.map { it.copy(state = "deleting", folderId = null) })
                selected.assetIds.toList().chunked(400).forEach { database.flightDao().unlinkMaterials(it) }
                selected.folderIds.toList().chunked(400).forEach { dao.deleteFolders(it) }
            }
        } } finally { reservation?.close() }
        val remaining = retryCleanup()
        refreshCatalogAfterChange()
        return remaining
    }
    suspend fun retryCleanup(): Int = withContext(Dispatchers.IO) {
        cleanupMutex.withLock {
            for (asset in dao.pendingCleanup()) {
                try {
                    usage.reserveDeletion(setOf(asset.id)).use {
                        storage.files.removeObject(asset.id)
                        gate.write { dao.finishCleanup(asset.id) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* The durable deleting row remains available for retry. */ }
            }
            dao.pendingCleanup().size
        }
    }
    private suspend fun refreshCatalogAfterChange() {
        try { writeCatalog() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { catalogProblem.value = "修改已保存，目录清单更新失败，请重试读取" }
    }
    fun observe(folderId: String?, limit: Int) = dao.observeAssets(folderId, limit)
    suspend fun position(asset: LibraryAsset) = dao.position(asset.folderId, asset.createdAt, asset.id)
    override suspend fun asset(id: String) = dao.asset(id)
    override suspend fun byHash(hash: String) = dao.byHash(hash)
    override suspend fun names(folderId: String?) = dao.names(folderId).toSet()
    override suspend fun location(asset: LibraryAsset) = "${LibraryTree(snapshot()).path(asset.folderId)} / ${asset.name}"
    override suspend fun publish(asset: LibraryAsset) { gate.write { database.withTransaction {
        val parent = asset.folderId?.takeIf { id -> dao.allFolders().any { it.id == id } }
        dao.insert(asset.copy(folderId = parent, name = uniqueLibraryName(asset.name, dao.names(parent).toSet())))
    } } }
    suspend fun saveCover(id: String, positionMillis: Long?, jpeg: ByteArray): Unit = withContext(Dispatchers.IO) {
        require(positionMillis == null || positionMillis >= 0L)
        require(jpeg.isNotEmpty() && jpeg.size <= 4 * 1024 * 1024) { "封面大小无效" }
        val owner = Any()
        check(usage.hold(owner, id)) { "素材正在删除" }
        try { coverMutex.withLock {
            gate.write {
                check(dao.asset(id)?.state == "ready") { "素材已不可用" }
                val file = AtomicFile(storage.files.cover(id))
                require(!file.baseFile.exists() || file.baseFile.length() <= 4 * 1024 * 1024) { "原封面无法读取" }
                val previous = try { file.openRead().use {
                    require(file.baseFile.length() <= 4 * 1024 * 1024) { "原封面无法读取" }
                    it.readBytes()
                } } catch (_: java.io.FileNotFoundException) { null }
                withContext(kotlinx.coroutines.NonCancellable) {
                    fun write(bytes: ByteArray) {
                        val out = file.startWrite()
                        try { out.write(bytes); file.finishWrite(out) }
                        catch (e: Throwable) { file.failWrite(out); throw e }
                    }
                    try {
                        write(jpeg)
                        database.withTransaction {
                            val asset = checkNotNull(dao.asset(id)?.takeIf { it.state == "ready" })
                            dao.updateAssets(listOf(asset.copy(coverFrameMillis = positionMillis)))
                        }
                    } catch (e: Throwable) {
                        try { if (previous == null) file.delete() else write(previous) } catch (_: Exception) { /* A valid committed cover remains; original media is untouched. */ }
                        throw e
                    }
                }
            }
            coverVersion.value++
            refreshCatalogAfterChange()
        } } finally { usage.release(owner) }
    }
    /** Room is authoritative on this device; refresh this portable index after each import. */
    suspend fun writeCatalog(): Unit = withContext(Dispatchers.IO) { catalogMutex.withLock {
        storage.files.initialize()
        val identity = AtomicFile(File(storage.files.root, "library-id"))
        val id = if (identity.baseFile.exists()) identity.openRead().bufferedReader().use { it.readText() }
            else UUID.randomUUID().toString().also { value ->
                val stream = identity.startWrite()
                try { stream.write(value.toByteArray()); identity.finishWrite(stream) }
                catch (e: Exception) { identity.failWrite(stream); throw e }
            }
        val snapshot = snapshot()
        val catalog = Catalog(version = 1, libraryId = id, assets = snapshot.assets.filter { it.state == "ready" }, folders = snapshot.folders)
        val file = AtomicFile(File(storage.files.root, "library.json"))
        val stream = file.startWrite()
        try { stream.write(Json.encodeToString(catalog).toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        catalogProblem.value = null
    } }
    @Serializable private data class Catalog(val version: Int, val libraryId: String,
        val assets: List<LibraryAsset>, val folders: List<LibraryFolder>)
}

data class LibraryDeletePreview(val folders: Int, val videos: Int, val records: Int,
    val folderIds: Set<String>, val assetIds: Set<String>)
