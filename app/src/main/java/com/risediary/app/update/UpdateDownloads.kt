package com.risediary.app.update

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface UpdateDownloads {
    suspend fun enqueue(release: GitHubRelease, asset: GitHubAsset, channel: UpdateChannel): DownloadRecord
    suspend fun query(record: DownloadRecord): DownloadState
    /** Preserve completion confirmed before removal; otherwise cancellation wins. */
    suspend fun cancel(record: DownloadRecord): DownloadCancellation
    suspend fun verifyForInstall(record: DownloadRecord): String
}

enum class DownloadCancellation { COMPLETED, CANCELLED }

internal enum class CancellationQueryState { COMPLETED, PRESENT, ABSENT }

/** DownloadManager query/remove are separate operations; never promise an atomic completion race. */
internal suspend fun cancelDownloadWithPolicy(
    query: suspend () -> CancellationQueryState,
    remove: suspend () -> Int
): DownloadCancellation {
    when (query()) {
        CancellationQueryState.COMPLETED -> return DownloadCancellation.COMPLETED
        CancellationQueryState.ABSENT -> return DownloadCancellation.CANCELLED
        CancellationQueryState.PRESENT -> Unit
    }
    if (remove() > 0) return DownloadCancellation.CANCELLED
    return when (query()) {
        CancellationQueryState.ABSENT -> DownloadCancellation.CANCELLED
        CancellationQueryState.COMPLETED -> DownloadCancellation.COMPLETED
        CancellationQueryState.PRESENT -> throw UpdateDownloadException(UpdateError.DOWNLOAD)
    }
}
class UpdateDownloadException(val reason: UpdateError) : IOException()

internal fun sha256Of(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

@Singleton
class SystemUpdateDownloads @Inject constructor(
    @ApplicationContext private val context: Context
) : UpdateDownloads {
    private fun manager(): DownloadManager =
        context.getSystemService(DownloadManager::class.java)
            ?: throw UpdateDownloadException(UpdateError.DOWNLOAD)

    override suspend fun enqueue(release: GitHubRelease, asset: GitHubAsset, channel: UpdateChannel) =
        withContext(Dispatchers.IO) {
            val safeName = asset.name.removeSuffix(".apk").replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
            val request = DownloadManager.Request(channel.downloadUrl(asset.downloadUrl).toUri())
                .setTitle("RiseDiary ${release.tagName}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "RiseDiary/$safeName-${System.currentTimeMillis()}-${asset.id}-${java.util.UUID.randomUUID()}.apk")
            DownloadRecord(manager().enqueue(request), release, asset, channel)
        }

    override suspend fun query(record: DownloadRecord): DownloadState = withContext(Dispatchers.IO) {
        val manager = manager()
        manager.query(DownloadManager.Query().setFilterById(record.id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext DownloadState.Failed(record, UpdateError.FILE_MISSING)
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (manager.getUriForDownloadedFile(record.id) == null) {
                        DownloadState.Failed(record, UpdateError.FILE_MISSING)
                    } else {
                        try {
                            manager.openDownloadedFile(record.id).use { }
                            DownloadState.Ready(record)
                        } catch (_: IOException) {
                            DownloadState.Failed(record, UpdateError.FILE_MISSING)
                        }
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    DownloadState.Failed(record, if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) UpdateError.STORAGE else UpdateError.DOWNLOAD)
                }
                DownloadManager.STATUS_PAUSED -> DownloadState.Paused(record,
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) == DownloadManager.PAUSED_QUEUED_FOR_WIFI)
                else -> DownloadState.Running(record, downloadPercent(
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))))
            }
        } ?: DownloadState.Failed(record, UpdateError.DOWNLOAD)
    }

    override suspend fun cancel(record: DownloadRecord): DownloadCancellation = withContext(Dispatchers.IO) {
        val manager = manager()
        cancelDownloadWithPolicy(
            query = {
                manager.query(DownloadManager.Query().setFilterById(record.id))?.use { cursor ->
                    if (!cursor.moveToFirst()) CancellationQueryState.ABSENT
                    else if (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL &&
                        manager.getUriForDownloadedFile(record.id) != null) {
                        try {
                            manager.openDownloadedFile(record.id).use { }
                            CancellationQueryState.COMPLETED
                        } catch (_: IOException) { CancellationQueryState.PRESENT }
                    } else CancellationQueryState.PRESENT
                } ?: throw UpdateDownloadException(UpdateError.DOWNLOAD)
            },
            remove = { manager.remove(record.id) }
        )
    }
    override suspend fun verifyForInstall(record: DownloadRecord): String = withContext(Dispatchers.IO) {
        val manager = manager()
        val uri = manager.getUriForDownloadedFile(record.id)
            ?: throw UpdateDownloadException(UpdateError.FILE_MISSING)
        try {
            manager.openDownloadedFile(record.id).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val expected = record.asset.sha256
                    if (expected != null && sha256Of(input) != expected) {
                        throw UpdateDownloadException(UpdateError.INTEGRITY)
                    }
                }
            }
        } catch (error: UpdateDownloadException) {
            throw error
        } catch (_: IOException) {
            throw UpdateDownloadException(UpdateError.FILE_MISSING)
        }
        uri.toString()
    }
}
