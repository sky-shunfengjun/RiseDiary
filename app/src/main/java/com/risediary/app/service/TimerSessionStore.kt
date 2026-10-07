package com.risediary.app.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private fun timerSessionDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = {
        File(context.filesDir, "datastore/timer_session.preferences_pb").apply { parentFile?.mkdirs() }
    })

@Singleton
class TimerSessionStore internal constructor(
    private val timerDataStore: DataStore<Preferences>,
    private val bootIdentity: BootIdentityProvider,
    private val elapsedClock: ElapsedRealtimeClock,
    private val wallClock: Clock
) {
    @Inject constructor(@ApplicationContext context: Context, bootIdentity: BootIdentityProvider,
        elapsedClock: ElapsedRealtimeClock, wallClock: Clock) :
        this(timerSessionDataStore(context), bootIdentity, elapsedClock, wallClock)

    suspend fun load(): TimerSession =
        loadAt(elapsedClock.millis(), wallClock.millis()).let { restored ->
            // Only active clocks survive a new process; ended form content does not.
            if (restored.isTerminal) TimerSession() else restored
        }

    internal suspend fun loadAt(elapsedRealtimeNow: Long, wallClockNow: Long): TimerSession {
        val snapshot = timerDataStore.data.first()
        val old = decode(snapshot)
        val needsUpgrade = (snapshot[KEY_DURATION_POLICY_VERSION] ?: 1) < DURATION_POLICY_VERSION ||
            (old.status != TimerStatus.IDLE && old.sessionId == null)
        val values = if (needsUpgrade) timerDataStore.edit { latest ->
            var current = decode(latest)
            var changed = false
            if ((latest[KEY_DURATION_POLICY_VERSION] ?: 1) < DURATION_POLICY_VERSION) {
                current = current.copy(notifiedMilestonesMask =
                    current.notifiedMilestonesMask and TimerMilestone.LIMIT.bit.inv())
                if (current.status == TimerStatus.LIMIT_REACHED) current = current.copy(
                    status = TimerStatus.FINISHED, resumedAtElapsedRealtime = 0L, resumedAtWallClock = 0L)
                changed = true
            }
            if (current.status != TimerStatus.IDLE && current.sessionId == null) {
                current = current.copy(sessionId = UUID.randomUUID().toString())
                changed = true
            }
            if (changed) encode(latest, current)
        } else snapshot
        return TimerMath.restore(decode(values), elapsedRealtimeNow, wallClockNow, bootIdentity.currentBootCount())
    }

    suspend fun save(session: TimerSession) { timerDataStore.edit { encode(it, session) } }

    private fun decode(values: Preferences): TimerSession {
        val status = TimerStatus.valueOf(values[KEY_STATUS] ?: TimerStatus.IDLE.name)
        val session = values[KEY_SESSION_JSON]?.let { payload ->
            require(payload.length <= 30_000)
            json.decodeFromString<TimerSession>(payload).also { require(it.status == status) }
        } ?: TimerSession(status = status,
            startedAtEpochMillis = values[KEY_STARTED_AT] ?: 0L,
            elapsedMillis = values[KEY_ELAPSED] ?: 0L,
            resumedAtElapsedRealtime = values[KEY_RESUMED_ELAPSED] ?: 0L,
            resumedAtWallClock = values[KEY_RESUMED_WALL] ?: 0L,
            notifiedMilestonesMask = values[KEY_NOTIFIED_MILESTONES] ?: 0,
            bootCount = values[KEY_BOOT_COUNT]?.takeIf { it >= 0 },
            sessionId = values[KEY_SESSION_ID])
        require(session.elapsedMillis in 0L..TimerMath.MAX_DURATION_MILLIS)
        require(session.sessionId == null || (session.sessionId.isNotBlank() && session.sessionId.length <= 128))
        require((session.kind == TimerKind.VIDEO) == (session.video != null))
        session.video?.let {
            require(com.risediary.app.media.validateLocalVideoFields(it.video.uriString, it.video.displayName, it.video.mimeType) == null)
            require(it.positionMillis >= 0L && it.speed in listOf(0.5f, 1f, 1.25f, 1.5f, 2f))
        }
        session.finishCandidate?.let {
            require(it.sessionId == session.sessionId && it.elapsedMillis in 0L..TimerMath.MAX_DURATION_MILLIS)
            require(it.priorStatus in setOf(TimerStatus.RUNNING, TimerStatus.PAUSED))
            require(it.requestedAtEpochMillis > 0L)
        }
        return session
    }

    private fun encode(values: MutablePreferences, session: TimerSession) {
        values[KEY_STATUS] = session.status.name
        values[KEY_STARTED_AT] = session.startedAtEpochMillis
        values[KEY_ELAPSED] = session.elapsedMillis
        values[KEY_RESUMED_ELAPSED] = session.resumedAtElapsedRealtime
        values[KEY_RESUMED_WALL] = session.resumedAtWallClock
        values[KEY_NOTIFIED_MILESTONES] = session.notifiedMilestonesMask
        session.bootCount?.takeIf { it >= 0 }?.let { values[KEY_BOOT_COUNT] = it } ?: values.remove(KEY_BOOT_COUNT)
        session.sessionId?.let { values[KEY_SESSION_ID] = it } ?: values.remove(KEY_SESSION_ID)
        values[KEY_SESSION_JSON] = json.encodeToString(session)
        values[KEY_DURATION_POLICY_VERSION] = DURATION_POLICY_VERSION
    }

    private companion object {
        val json = Json { encodeDefaults = true }
        const val DURATION_POLICY_VERSION = 2
        val KEY_SESSION_JSON = stringPreferencesKey("session_json")
        val KEY_SESSION_ID = stringPreferencesKey("session_id")
        val KEY_DURATION_POLICY_VERSION = intPreferencesKey("duration_policy_version")
        val KEY_BOOT_COUNT = intPreferencesKey("boot_count")
        val KEY_STATUS = stringPreferencesKey("status")
        val KEY_STARTED_AT = longPreferencesKey("started_at_epoch")
        val KEY_ELAPSED = longPreferencesKey("elapsed_millis")
        val KEY_RESUMED_ELAPSED = longPreferencesKey("resumed_elapsed_realtime")
        val KEY_RESUMED_WALL = longPreferencesKey("resumed_wall_clock")
        val KEY_NOTIFIED_MILESTONES = intPreferencesKey("notified_milestones")
    }
}
