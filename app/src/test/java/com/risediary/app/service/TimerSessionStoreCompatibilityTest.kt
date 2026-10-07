package com.risediary.app.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TimerSessionStoreCompatibilityTest {
    @Test fun oldTwoHourLimitRemainsFinishedAndKeepsEarlierReminderMarks() = runTest {
        val data = MemoryStore(fixture(TimerStatus.LIMIT_REACHED, 7_200_000L, mask = 15))
        val restored = store(data).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.FINISHED, restored.status)
        assertEquals(7_200_000L, restored.elapsedMillis)
        assertEquals(123_000L, restored.startedAtEpochMillis)
        assertEquals(0L, restored.resumedAtElapsedRealtime)
        assertEquals(0L, restored.resumedAtWallClock)
        assertEquals(7, restored.notifiedMilestonesMask)
        assertEquals("FINISHED", data.current[statusKey])
        assertEquals(2, data.current[versionKey])
        assertEquals(restored, store(data).loadAt(11_000L, 999_000L))
        assertEquals(1, data.writes)
    }

    @Test fun oldRunningSessionAtTwoHoursContinuesAndClearsOnlyTheObsoleteLimitMark() = runTest {
        val data = MemoryStore(fixture(TimerStatus.RUNNING, 7_200_000L, mask = 15))
        val restored = store(data).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.RUNNING, restored.status)
        assertEquals(7_201_000L, restored.elapsedMillis)
        assertEquals(7, restored.notifiedMilestonesMask)
        val atNewLimit = prepareTimerTransition(restored.copy(elapsedMillis = 86_400_000L))
        assertEquals(TimerMilestone.LIMIT, atNewLimit.milestone)
        assertEquals(15, atNewLimit.session.notifiedMilestonesMask)
    }

    @Test fun oldFinishedSessionNeverRestarts() = runTest {
        val data = MemoryStore(fixture(TimerStatus.FINISHED, 10_800_000L, mask = 11))
        val restored = store(data).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.FINISHED, restored.status)
        assertEquals(10_800_000L, restored.elapsedMillis)
        assertEquals(3, restored.notifiedMilestonesMask)
    }

    @Test fun oldPausedSessionDoesNotAccumulateTimeDuringUpgrade() = runTest {
        val data = MemoryStore(fixture(TimerStatus.PAUSED, 43_200_000L, mask = 7))
        val restored = store(data).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.PAUSED, restored.status)
        assertEquals(43_200_000L, restored.elapsedMillis)
        assertEquals(7, restored.notifiedMilestonesMask)
    }

    @Test fun runningSessionFromAnEarlierBootBecomesPausedAtSavedElapsed() = runTest {
        val data = MemoryStore(fixture(TimerStatus.RUNNING, 10_800_000L, boot = 4))
        val restored = store(data, boot = 5).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.PAUSED, restored.status)
        assertEquals(10_800_000L, restored.elapsedMillis)
        assertEquals(0L, restored.resumedAtElapsedRealtime)
    }

    @Test fun newTwentyFourHourLimitKeepsItsNotificationMarkAcrossLoads() = runTest {
        val data = MemoryStore(fixture(TimerStatus.LIMIT_REACHED, 86_400_000L, mask = 15, version = 2))
        val restored = store(data).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.LIMIT_REACHED, restored.status)
        assertEquals(86_400_000L, restored.elapsedMillis)
        assertEquals(15, restored.notifiedMilestonesMask)
        assertEquals(restored, store(data).loadAt(11_000L, 999_000L))
        assertEquals(0, data.writes)
    }

    @Test fun failedUpgradeDoesNotPublishOrChangeStoredSessionAndCanRetry() = runTest {
        val original = fixture(TimerStatus.LIMIT_REACHED, 7_200_000L, mask = 15)
        val data = MemoryStore(original).apply { failWrites = true }
        val sessionStore = store(data)
        assertTrue(runCatching { sessionStore.loadAt(11_000L, 999_000L) }.exceptionOrNull() is IOException)
        assertEquals(original, data.current)
        data.failWrites = false
        assertEquals(TimerStatus.FINISHED, sessionStore.loadAt(11_000L, 999_000L).status)
        assertEquals(2, data.current[versionKey])
    }

    @Test fun aNewSaveDuringUpgradeIsNotReplacedByTheEarlierSnapshot() = runTest {
        val data = MemoryStore(fixture(TimerStatus.LIMIT_REACHED, 7_200_000L, mask = 15))
        data.beforeUpdate = {
            data.current = fixture(TimerStatus.LIMIT_REACHED, 86_400_000L, mask = 15, version = 2)
        }
        val restored = store(data).loadAt(11_000L, 999_000L)
        assertEquals(TimerStatus.LIMIT_REACHED, restored.status)
        assertEquals(86_400_000L, restored.elapsedMillis)
        assertEquals(15, restored.notifiedMilestonesMask)
    }

    @Test fun savesUseNewPolicyAndRoundTripFinishedDuration() = runTest {
        val data = MemoryStore(preferencesOf())
        val sessionStore = store(data)
        val expected = TimerSession(TimerStatus.FINISHED, startedAtEpochMillis = 123_000L,
            elapsedMillis = 86_400_000L, notifiedMilestonesMask = 15, bootCount = 4, sessionId = "compatibility")
        sessionStore.save(expected)
        assertEquals(2, data.current[versionKey])
        assertEquals(expected, sessionStore.loadAt(11_000L, 999_000L))
    }


    @Test fun oldSessionWithoutLiveUpdateStateRemainsReadable() = runTest {
        val data = MemoryStore(fixture(TimerStatus.PAUSED, 5000L, version = 2))
        assertFalse(store(data).load().liveUpdateDismissed)
    }

    @Test fun userDismissalSurvivesAProcessRestoreWithoutChangingElapsedTime() = runTest {
        val data = MemoryStore(fixture(TimerStatus.PAUSED, 5000L, version = 2))
        val initial = store(data).load().copy(liveUpdateDismissed = true)
        store(data).save(initial)
        val restored = store(data).load()
        assertTrue(restored.liveUpdateDismissed)
        assertEquals(5000L, restored.elapsedMillis)
        assertEquals(initial.sessionId, restored.sessionId)
    }

    private fun store(data: DataStore<Preferences>, boot: Int = 4) = TimerSessionStore(
        data,
        BootIdentityProvider { boot },
        object : ElapsedRealtimeClock { override fun millis() = 11_000L },
        Clock.fixed(Instant.ofEpochMilli(999_000L), ZoneOffset.UTC)
    )

    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        var current: Preferences
            get() = state.value
            set(value) { state.value = value }
        var writes = 0
        var failWrites = false
        var beforeUpdate: (() -> Unit)? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            beforeUpdate?.also { beforeUpdate = null }?.invoke()
            if (failWrites) throw IOException("storage unavailable")
            return transform(current).also { current = it; writes++ }
        }
    }

    private fun fixture(status: TimerStatus, elapsed: Long, mask: Int = 0, boot: Int = 4,
                        version: Int? = null): Preferences {
        val values = preferencesOf(
            statusKey to status.name,
            stringPreferencesKey("session_id") to "compatibility",
            longPreferencesKey("started_at_epoch") to 123_000L,
            longPreferencesKey("elapsed_millis") to elapsed,
            longPreferencesKey("resumed_elapsed_realtime") to 10_000L,
            longPreferencesKey("resumed_wall_clock") to 888_000L,
            intPreferencesKey("notified_milestones") to mask,
            intPreferencesKey("boot_count") to boot
        ).toMutablePreferences()
        if (version != null) values[versionKey] = version
        return values.toPreferences()
    }

    private companion object {
        val statusKey = stringPreferencesKey("status")
        val versionKey = intPreferencesKey("duration_policy_version")
    }
}
