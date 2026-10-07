package com.risediary.app.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlaybackSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TimerSessionIdentityRecoveryTest {
    @Test fun legacyIdentityIsSavedOnceAndSurvivesANewStoreInstance() = runTest {
        val disk = MemoryDisk(preferencesOf(stringPreferencesKey("status") to "PAUSED",
            intPreferencesKey("duration_policy_version") to 2))
        val first = store(disk).load()
        assertFalse(first.sessionId.isNullOrBlank())
        assertEquals(first.sessionId, store(disk).load().sessionId)
        assertEquals(1, disk.writes)
    }

    @Test fun videoAndFinishCandidateSurviveDurableRoundTrip() = runTest {
        val disk = MemoryDisk(preferencesOf())
        val video = VideoPlaybackSnapshot(LocalVideoRef("content://videos/document/1", "本地视频", "video/mp4"),
            13_000L, 1.5f, true)
        val expected = TimerSession(status = TimerStatus.PAUSED, sessionId = "s1", kind = TimerKind.VIDEO,
            startedAtEpochMillis = 100_000L, elapsedMillis = 8_000L, bootCount = 1, video = video,
            finishCandidate = TimerFinishCandidate("s1", 112_000L, 8_000L, TimerStatus.PAUSED))
        store(disk).save(expected)
        assertEquals(expected, store(disk).load())
    }

    @Test fun corruptStatusMustNotPretendThatThereIsNoTimer() = runTest {
        val disk = MemoryDisk(preferencesOf(stringPreferencesKey("status") to "BROKEN",
            intPreferencesKey("duration_policy_version") to 2))
        assertTrue(runCatching { store(disk).load() }.isFailure)
        assertEquals(0, disk.writes)
    }

    @Test fun finishedAndLimitReachedContentAreNotRecoveredAsNewForms() = runTest {
        for (status in listOf(TimerStatus.FINISHED, TimerStatus.LIMIT_REACHED)) {
            val disk = MemoryDisk(preferencesOf())
            store(disk).save(TimerSession(status = status, sessionId = "ended",
                startedAtEpochMillis = 100_000L, elapsedMillis = 8_000L, endedAtEpochMillis = 112_000L))
            assertEquals(TimerSession(), store(disk).load())
        }
    }

    private fun store(disk: MemoryDisk) = TimerSessionStore(disk, BootIdentityProvider { 1 },
        object : ElapsedRealtimeClock { override fun millis() = 20_000L },
        Clock.fixed(Instant.ofEpochMilli(120_000L), ZoneOffset.UTC))
    private class MemoryDisk(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        var writes = 0
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it; writes++ }
    }
}
