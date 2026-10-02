package com.risediary.app.update

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface UpdatePreferences {
    val settings: Flow<UpdateSettings>
    val downloadRecord: Flow<DownloadRecord?>
    suspend fun setAutomaticCheck(enabled: Boolean)
    suspend fun setChannel(channel: UpdateChannel)
    suspend fun setForceCheck(enabled: Boolean)
    suspend fun setReleaseChannel(channel: ReleaseChannel)
    suspend fun setDeveloperEnabled(enabled: Boolean)
    suspend fun restoreDeveloperDefaults()
    suspend fun saveDownload(record: DownloadRecord?)
}

/** Device-local update preferences and system task IDs never enter diary backups. */
@Singleton
class DiskUpdatePreferences @Inject constructor(
    @ApplicationContext context: Context
) : UpdatePreferences {
    private val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(context.filesDir, "datastore/updates.preferences_pb").apply { parentFile?.mkdirs() } }
    )
    private val json = Json { ignoreUnknownKeys = true }
    override val settings = store.data.map { it.updateSettings() }
    override val downloadRecord = store.data.map { prefs ->
        prefs[RECORD]?.let { value ->
            runCatching { json.decodeFromString<DownloadRecord>(value) }.getOrNull()?.takeIf {
                it.id > 0 && isProjectApkUrl(it.asset.downloadUrl)
            }
        }
    }
    override suspend fun setAutomaticCheck(enabled: Boolean) { store.edit { it[AUTOMATIC] = enabled } }
    override suspend fun setChannel(channel: UpdateChannel) { store.edit { it[CHANNEL] = channel.name } }
    override suspend fun setForceCheck(enabled: Boolean) { store.edit { it[FORCE] = enabled } }
    override suspend fun setReleaseChannel(channel: ReleaseChannel) { store.edit { it[RELEASE_CHANNEL] = channel.name } }
    override suspend fun setDeveloperEnabled(enabled: Boolean) { store.edit { it[DEVELOPER] = enabled } }
    override suspend fun restoreDeveloperDefaults() { store.edit { it.restoreDeveloperSettings() } }
    override suspend fun saveDownload(record: DownloadRecord?) {
        store.edit { prefs ->
            if (record == null) prefs.remove(RECORD)
            else prefs[RECORD] = json.encodeToString(record)
        }
    }
}

internal val AUTOMATIC = booleanPreferencesKey("automatic_check")
internal val CHANNEL = stringPreferencesKey("apk_channel")
internal val RECORD = stringPreferencesKey("download_record")
internal val FORCE = booleanPreferencesKey("force_check")
internal val RELEASE_CHANNEL = stringPreferencesKey("release_channel")
internal val DEVELOPER = booleanPreferencesKey("developer_enabled")

internal fun Preferences.updateSettings() = UpdateSettings(
    automaticCheck = this[AUTOMATIC] ?: true,
    channel = runCatching { UpdateChannel.valueOf(this[CHANNEL].orEmpty()) }.getOrDefault(UpdateChannel.OFFICIAL),
    forceCheck = this[FORCE] ?: false,
    releaseChannel = runCatching { ReleaseChannel.valueOf(this[RELEASE_CHANNEL].orEmpty()) }.getOrDefault(ReleaseChannel.STABLE),
    developerEnabled = this[DEVELOPER] ?: false,
)

/** Called in a single DataStore edit; unrelated keys and the download record remain untouched. */
internal fun MutablePreferences.restoreDeveloperSettings() {
    this[FORCE] = false
    this[RELEASE_CHANNEL] = ReleaseChannel.STABLE.name
    this[DEVELOPER] = false
}