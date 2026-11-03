package com.risediary.app.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import java.util.UUID
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Uses a real Room database and private, test-owned UUID directories. Run on a phone. */
class LibraryManagementTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var storage: LibraryStorage
    private val usage = LibraryUsageRegistry()
    private val owned = mutableListOf<String>()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        storage = LibraryStorage(LibraryFileStorage(File(context.cacheDir, "library-test-${UUID.randomUUID()}")))
        repository = LibraryRepository(db, storage, DataMaintenanceGate(), usage)
    }
    @After fun cleanup() { owned.forEach { storage.files.discardObject(it) }; storage.files.root.deleteRecursively(); db.close() }
    private suspend fun add(folder: String? = null): LibraryAsset {
        val id = UUID.randomUUID().toString(); owned.add(id)
        val asset = LibraryAsset(id, folder, "video", "原名.mp4", "视频.mp4", "video/mp4", 3, 1000, id, null, 1, "ready")
        db.libraryDao().insert(asset)
        storage.files.media(id).parentFile!!.mkdirs()
        storage.files.media(id).writeBytes(byteArrayOf(1, 2, 3))
        return asset
    }
    @Test fun deletingFolderUnlinksOnlyPlaybackAndPreservesDiaryData() = runBlocking {
        val a = UUID.randomUUID().toString(); val b = UUID.randomUUID().toString()
        db.libraryDao().insertFolder(LibraryFolder(a, null, "父", 1))
        db.libraryDao().insertFolder(LibraryFolder(b, a, "子", 2))
        val asset = add(b)
        val flight = Flight(startTime = 10, endTime = 20, durationSeconds = 10, spurtCount = 2,
            semenVolumeMl = null, ejaculationDistanceCm = 1f, methodTags = "[]", moodNote = "保留备注",
            materialId = asset.id, materialName = "名称快照")
        val record = db.flightDao().insert(flight)
        assertEquals(LibraryDeletePreview(2, 1, 1, setOf(a, b), setOf(asset.id)), repository.previewDelete(setOf("folder:$a", "folder:$b", "asset:${asset.id}")))
        assertEquals(0, repository.delete(setOf("folder:$a")))
        assertEquals(flight.copy(id = record, materialId = null, materialRemoved = true), db.flightDao().getById(record))
        assertTrue(db.libraryDao().allFolders().isEmpty())
        assertNull(db.libraryDao().asset(asset.id))
        assertFalse(storage.files.media(asset.id).exists())
    }
    @Test fun savingAndRestoringCoverOnlyChangesTheCoverAndItsFrameMetadata() = runBlocking {
        val asset = add()
        val old = byteArrayOf(7, 8, 9)
        val next = byteArrayOf(10, 11, 12)
        storage.files.cover(asset.id).writeBytes(old)
        repository.saveCover(asset.id, 300L, next)
        assertArrayEquals(next, storage.files.cover(asset.id).readBytes())
        assertEquals(300L, db.libraryDao().asset(asset.id)?.coverFrameMillis)
        assertArrayEquals(byteArrayOf(1, 2, 3), storage.files.media(asset.id).readBytes())
        repository.saveCover(asset.id, null, old)
        assertNull(db.libraryDao().asset(asset.id)?.coverFrameMillis)
        assertArrayEquals(old, storage.files.cover(asset.id).readBytes())
        assertEquals(2L, repository.coverRevision.value)
    }
    @Test fun invalidCoverOrUnavailableMaterialNeverReplacesTheExistingCover() = runBlocking {
        val asset = add()
        val original = byteArrayOf(7, 8, 9)
        storage.files.cover(asset.id).writeBytes(original)
        assertTrue(runCatching { repository.saveCover(asset.id, 300L, byteArrayOf()) }.isFailure)
        db.libraryDao().updateAssets(listOf(asset.copy(state = "deleting")))
        assertTrue(runCatching { repository.saveCover(asset.id, 300L, byteArrayOf(5)) }.isFailure)
        assertArrayEquals(original, storage.files.cover(asset.id).readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), storage.files.media(asset.id).readBytes())
    }
    @Test fun activeLeaseAbortsEntireDeletionBeforeChangingAnyRecord() = runBlocking {
        val asset = add(); val owner = Any()
        usage.hold(owner, asset.id)
        try { repository.delete(setOf("asset:${asset.id}")); fail("active deletion was allowed") }
        catch (_: IllegalStateException) { }
        assertEquals(asset, db.libraryDao().asset(asset.id))
        assertTrue(storage.files.media(asset.id).exists())
        usage.release(owner)
    }
    @Test fun moveChangesOnlyMetadataAndRestartCanFinishPendingCleanup() = runBlocking {
        val asset = add()
        repository.createFolder(null, "目标")
        val folder = db.libraryDao().allFolders().single().id
        repository.move(setOf("asset:${asset.id}"), folder)
        assertEquals(asset.copy(folderId = folder), db.libraryDao().asset(asset.id))
        assertArrayEquals(byteArrayOf(1, 2, 3), storage.files.media(asset.id).readBytes())
        db.libraryDao().updateAssets(listOf(asset.copy(state = "deleting", folderId = null)))
        val restarted = LibraryRepository(db, storage, DataMaintenanceGate(), LibraryUsageRegistry())
        assertEquals(0, restarted.retryCleanup())
        assertNull(db.libraryDao().asset(asset.id))
    }
    @Test fun changedDeletePreviewNeverDeletesUnreviewedContents() = runBlocking {
        repository.createFolder(null, "待删除")
        val folder = db.libraryDao().allFolders().single().id
        val first = add(folder)
        val keys = setOf("folder:$folder")
        val preview = repository.previewDelete(keys)
        val second = add(folder)
        try { repository.delete(keys, preview); fail("stale preview was accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(first, db.libraryDao().asset(first.id))
        assertEquals(second, db.libraryDao().asset(second.id))
        assertEquals(1, db.libraryDao().allFolders().size)
    }
}
