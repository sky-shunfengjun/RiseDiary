package com.risediary.app.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private fun timerSessionDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = {
            File(context.filesDir, "datastore/timer_session.preferences_pb")
                .apply { parentFile?.mkdirs() }
        }
    )

@Singleton
class TimerSessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val timerDataStore: DataStore<Preferences> = timerSessionDataStore(context)

    suspend fun load(): TimerSession {
        return try {
            val values = timerDataStore.data.first()
            val status = runCatching {
                TimerStatus.valueOf(values[KEY_STATUS] ?: TimerStatus.IDLE.name)
            }.getOrDefault(TimerStatus.IDLE)
            TimerSession(
                status = status,
                startedAtEpochMillis = values[KEY_STARTED_AT] ?: 0L,
                elapsedMillis = values[KEY_ELAPSED] ?: 0L,
                resumedAtElapsedRealtime = values[KEY_RESUMED_ELAPSED] ?: 0L,
                resumedAtWallClock = values[KEY_RESUMED_WALL] ?: 0L,
                notifiedMilestonesMask = values[KEY_NOTIFIED_MILESTONES] ?: 0
            )
        } catch (error: IOException) {
            TimerSession()
        }
    }

    suspend fun save(session: TimerSession) {
        timerDataStore.edit { values ->
            values[KEY_STATUS] = session.status.name
            values[KEY_STARTED_AT] = session.startedAtEpochMillis
            values[KEY_ELAPSED] = session.elapsedMillis
            values[KEY_RESUMED_ELAPSED] = session.resumedAtElapsedRealtime
            values[KEY_RESUMED_WALL] = session.resumedAtWallClock
            values[KEY_NOTIFIED_MILESTONES] = session.notifiedMilestonesMask
        }
    }

    private companion object {
        val KEY_STATUS = stringPreferencesKey("status")
        val KEY_STARTED_AT = longPreferencesKey("started_at_epoch")
        val KEY_ELAPSED = longPreferencesKey("elapsed_millis")
        val KEY_RESUMED_ELAPSED = longPreferencesKey("resumed_elapsed_realtime")
        val KEY_RESUMED_WALL = longPreferencesKey("resumed_wall_clock")
        val KEY_NOTIFIED_MILESTONES = intPreferencesKey("notified_milestones")
    }
}
