package com.risediary.app.data.library

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LibraryVideoInfo(val durationMillis: Long?, val mime: String, val coverFrameMillis: Long? = null)

/** Runs on IO. The index only sees complete, verified, video-inspected objects. */
class LibraryImportProcessor(private val storage: LibraryFileStorage, private val index: LibraryAssetIndex,
    private val inspect: (File) -> LibraryVideoInfo) {
    // The source owns its stream and closes it around the supplied suspend consumer.
    suspend fun importSource(item: LibraryImportItem,
        source: suspend (suspend (InputStream, Long?) -> LibraryImportItem) -> LibraryImportItem,
        progress: (Long) -> Unit = {}): LibraryImportItem {
        index.asset(item.id)?.let {
            storage.discardStaging(item.id)
            return item.copy(status = "done", detail = it.name)
        }
        try {
            return source { input, size -> import(item, input, size, progress) }
        } finally { storage.discardStaging(item.id) }
    }
    suspend fun import(item: LibraryImportItem, input: InputStream, expectedSize: Long?,
        progress: (Long) -> Unit = {}): LibraryImportItem {
        // A process can die after the DB commit and before the task checkpoint.
        index.asset(item.id)?.let { return item.copy(status = "done", detail = it.name) }
        var published = false
        try {
            storage.discardObject(item.id) // only this uncommitted task's owned UUID
            val context = currentCoroutineContext()
            val copied = storage.copy(item.id, input, expectedSize, { context.ensureActive() }, progress)
            index.byHash(copied.sha256)?.let {
                return item.copy(status = "duplicate", detail = index.location(it))
            }
            val info = inspect(File(storage.staging(item.id), "media.bin"))
            context.ensureActive()
            val name = uniqueLibraryName(item.name, index.names(item.folderId))
            storage.commit(item.id, copied) { context.ensureActive() }
            // No cancellable gap between publication on disk and its DB row.
            withContext(NonCancellable) {
                index.publish(LibraryAsset(item.id, item.folderId, "video", item.name, name, info.mime,
                    copied.size, info.durationMillis, copied.sha256, info.coverFrameMillis,
                    System.currentTimeMillis(), "ready"))
                published = true
            }
            return item.copy(status = "done", detail = name)
        } finally {
            storage.discardStaging(item.id)
            if (!published) storage.discardObject(item.id)
        }
    }
}
