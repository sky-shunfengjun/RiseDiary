package com.risediary.app.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "timer_session"
)

@Singleton
class TimerSessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun load(): TimerSession {
        val values = context.timerDataStore.data.first()
        val status = runCatching {
            TimerStatus.valueOf(values[KEY_STATUS] ?: TimerStatus.IDLE.name)
        }.getOrDefault(TimerStatus.IDLE)
        return TimerSession(
            status = status,
            startedAtEpochMillis = values[KEY_STARTED_AT] ?: 0L,
            elapsedMillis = values[KEY_ELAPSED] ?: 0L,
            resumedAtElapsedRealtime = values[KEY_RESUMED_ELAPSED] ?: 0L,
            resumedAtWallClock = values[KEY_RESUMED_WALL] ?: 0L,
            notifiedMilestonesMask = values[KEY_NOTIFIED_MILESTONES] ?: 0
        )
    }

    suspend fun save(session: TimerSession) {
        context.timerDataStore.edit { values ->
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
