package com.risediary.app.data.library

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryCoverFrame(val positionMillis: Long, val durationMillis: Long, val jpeg: ByteArray, val automatic: Boolean = false)
internal object LibraryCoverFileAccess { val mutex = Mutex() }

/** One bounded native extraction at a time. Original media is only opened for reading. */
@Singleton
class LibraryCoverManager @Inject constructor(private val storage: LibraryStorage,
    private val repository: LibraryRepository, private val usage: LibraryUsageRegistry) {
    private val extraction = Mutex()
    suspend fun preview(id: String, position: Long?): LibraryCoverFrame = withContext(Dispatchers.IO) {
        val owner = Any()
        check(usage.hold(owner, id)) { "素材正在删除" }
        try {
            check(repository.asset(id)?.state == "ready") { "素材已不可用" }
            extraction.withLock { currentCoroutineContext().ensureActive(); extractLibraryCover(storage.files.media(id), position) }
        } finally { usage.release(owner) }
    }
    suspend fun save(id: String, frame: LibraryCoverFrame) = repository.saveCover(id, if (frame.automatic) null else frame.positionMillis, frame.jpeg)
}

internal fun extractLibraryCover(file: File, position: Long?): LibraryCoverFrame {
    val reader = MediaMetadataRetriever()
    try {
        reader.setDataSource(file.absolutePath)
        val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val at = (position ?: minOf(1000L, duration / 10)).coerceIn(0L, (duration - 1L).coerceAtLeast(0L)).coerceAtMost(Long.MAX_VALUE / 1000L)
        val bitmap = reader.getScaledFrameAtTime(at * 1000L,
            if (position == null) MediaMetadataRetriever.OPTION_CLOSEST_SYNC else MediaMetadataRetriever.OPTION_CLOSEST, 1024, 1024)
            ?: error("无法读取这个画面，请换一个位置重试")
        try {
            val bytes = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) { "封面生成失败" }
                out.toByteArray()
            }
            return LibraryCoverFrame(at, duration, bytes, position == null)
        } finally { bitmap.recycle() }
    } finally { reader.release() }
}
