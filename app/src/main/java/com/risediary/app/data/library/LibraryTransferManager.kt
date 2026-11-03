package com.risediary.app.data.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.media.VideoGrantRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class LibraryTransferManager @Inject constructor(@ApplicationContext private val context: Context,
    private val storage: LibraryStorage, private val repository: LibraryRepository,
    private val grants: VideoGrantRegistry, private val gate: DataMaintenanceGate) {
    private val journal = LibraryTransferJournal(File(context.filesDir, "library-transfer.json"))
    private val mutex = Mutex()
    private var loaded = false
    @Volatile private var activeJob: Job? = null
    private val mutable = MutableStateFlow(LibraryTransferState())
    val state = mutable.asStateFlow()
    private suspend fun load() {
        if (!loaded) { mutable.value = journal.read(); loaded = true }
    }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { load() }
    }
    suspend fun enqueue(uris: List<Uri>, folderId: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            check(!mutable.value.running) { "正在导入，请等待完成" }
            val pending = mutable.value.remaining
            val new = uris.distinct().filter { uri -> pending.none { it.uri == uri.toString() } }.map { uri ->
                val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                    null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
                LibraryImportItem(UUID.randomUUID().toString(), uri.toString(), name?.take(1000)?.takeIf { it.isNotBlank() } ?: "视频", folderId)
            }
            val next = LibraryTransferState(items = pending + new, message = "准备导入")
            journal.write(next); mutable.value = next
            val retained = next.remaining.map { it.uri }.toSet()
            for (uri in uris.distinct()) {
                // Only original picker URIs use the grant ledger; library playback never does.
                grants.acquire("library-import", uri.toString(), Intent.FLAG_GRANT_READ_URI_PERMISSION, retained)
            }
        }
    }
    fun cancel() { activeJob?.cancel(CancellationException("导入已暂停")) }
    suspend fun dismissResult(discardPending: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            if (!mutable.value.running && (mutable.value.remaining.isEmpty() || discardPending)) {
                for (item in mutable.value.remaining) {
                    storage.files.discardStaging(item.id)
                    if (repository.asset(item.id) == null) storage.files.discardObject(item.id)
                }
                val empty = LibraryTransferState()
                journal.write(empty); mutable.value = empty
                grants.requestCleanup()
            }
        }
    }
    suspend fun run() = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            activeJob = currentCoroutineContext()[Job]
            var status = "导入完成"
            try {
                mutable.value = mutable.value.copy(running = true, message = "正在导入", bytes = 0, totalBytes = null)
                journal.write(mutable.value)
                val processor = LibraryImportProcessor(storage.files, repository, ::inspect)
                for (item in mutable.value.remaining) {
                    currentCoroutineContext().ensureActive()
                    val updated = try {
                            var lastProgress = 0L
                            val result = processor.importSource(item, source = { consume ->
                                val size = context.contentResolver.query(item.uri.toUri(), arrayOf(OpenableColumns.SIZE),
                                    null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).takeIf { n -> n >= 0 } else null }
                                mutable.value = mutable.value.copy(bytes = 0, totalBytes = size)
                                context.contentResolver.openInputStream(item.uri.toUri())?.use { input -> consume(input, size) }
                                    ?: error("无法读取原视频，请重新选择")
                            }, progress = { count ->
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - lastProgress >= 200) {
                                        mutable.value = mutable.value.copy(bytes = count); lastProgress = now
                                    }
                            })
                            gate.write { repository.writeCatalog() }
                            result
                    } catch (e: CancellationException) { throw e }
                    catch (_: SecurityException) { item.copy(status = "failed", detail = "读取授权已失效，请重新选择原视频") }
                    catch (e: Exception) { item.copy(status = "failed", detail = e.message?.take(120) ?: "导入失败，请检查空间后重试") }
                    mutable.value = mutable.value.copy(items = mutable.value.items.map { if (it.id == item.id) updated else it })
                    journal.write(mutable.value)
                    grants.retain("library-import", mutable.value.remaining.map { it.uri }.toSet())
                    grants.requestCleanup()
                }
                if (mutable.value.remaining.isNotEmpty()) status = "部分项目未完成，可重试；已完成的素材保留"
            } catch (_: CancellationException) { status = "导入已中断，已完成的素材保留，可重试剩余项目" }
            catch (_: Exception) { status = "无法保存导入状态，请检查可用空间后重试" }
            finally {
                withContext(NonCancellable) {
                    mutable.value = mutable.value.copy(running = false, bytes = 0, totalBytes = null, message = status)
                    runCatching { journal.write(mutable.value) }
                    grants.forget("library-import")
                }
                activeJob = null
            }
        }
    }
    private fun inspect(file: File): LibraryVideoInfo {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.absolutePath)
            require(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "文件没有可读取的视频画面" }
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val mime = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
            val at = minOf(1000L, (duration ?: 0L) / 10).coerceAtLeast(0)
            try {
                val frame = reader.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
                frame?.let { bitmap ->
                    try { FileOutputStream(File(file.parentFile, "cover.bin")).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                    finally { bitmap.recycle() }
                }
            } catch (_: Exception) { /* Cover failure never blocks a valid video. */ }
            return LibraryVideoInfo(duration, mime, at)
        } finally { reader.release() }
    }
}
