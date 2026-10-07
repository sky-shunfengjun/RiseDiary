package com.risediary.app.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The ledger contains only video grants acquired by this feature, never backup-picker grants. */
@Singleton
class AndroidVideoFileAccess @Inject constructor(@ApplicationContext context: Context) : VideoFileAccess {
    private val resolver = context.contentResolver
    private val ledger = PreferenceDataStoreFactory.create(produceFile = {
        File(context.filesDir, "datastore/video_grants.preferences_pb").apply { parentFile?.mkdirs() }
    })
    private val managedKey = stringSetPreferencesKey("managed_video_uris")

    override suspend fun acquire(uriString: String, flags: Int): Result<LocalVideoRef> =
        withContext(Dispatchers.IO) {
            if (!isSupportedLocalVideoUri(uriString) || flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
                return@withContext Result.failure(IllegalArgumentException("请重新选择视频"))
            }
            val uri = Uri.parse(uriString)
            val previouslyGranted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            var acquired = false
            try {
                // Do not acquire a new grant if the ownership ledger cannot be read.
                ledger.data.first()
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                acquired = true
                resolver.openFileDescriptor(uri, "r")?.use { } ?: throw FileNotFoundException()
                val name = runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && it.moveToFirst()) it.getString(index) else null
                    }
                }.getOrNull()?.takeIf(String::isNotBlank) ?: "本地视频"
                val mime = resolver.getType(uri)
                require(validateLocalVideoFields(uriString, name, mime) == null) { "请选择视频文件" }
                ledger.edit { it[managedKey] = it[managedKey].orEmpty() + uriString }
                Result.success(LocalVideoRef(uriString, name, mime))
            } catch (error: Exception) {
                if (acquired && !previouslyGranted) withContext(NonCancellable) {
                    runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                if (error is CancellationException) throw error
                Result.failure(error)
            }
        }

    override suspend fun check(video: LocalVideoRef): VideoAccessState = withContext(Dispatchers.IO) {
        if (validateLocalVideoFields(video.uriString, video.displayName, video.mimeType) != null) {
            return@withContext VideoAccessState.INVALID
        }
        try {
            resolver.openFileDescriptor(Uri.parse(video.uriString), "r")?.use { }
                ?: return@withContext VideoAccessState.MISSING
            VideoAccessState.READABLE
        } catch (_: SecurityException) {
            VideoAccessState.PERMISSION_LOST
        } catch (_: FileNotFoundException) {
            VideoAccessState.MISSING
        } catch (_: IOException) {
            VideoAccessState.MISSING
        } catch (_: IllegalArgumentException) {
            VideoAccessState.INVALID
        }
    }

    override suspend fun releaseUnused(referencedUris: Set<String>) = withContext(Dispatchers.IO) {
        val managed = ledger.data.first()[managedKey].orEmpty()
        val released = mutableSetOf<String>()
        for (value in managed - referencedUris) {
            val uri = Uri.parse(value)
            try {
                if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                    resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                released += value
            } catch (_: SecurityException) {
                // Revoked externally is already gone; a still-present grant is retried next time.
                if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) released += value
            }
        }
        if (released.isNotEmpty()) ledger.edit { it[managedKey] = it[managedKey].orEmpty() - released }
        Unit
    }
}
