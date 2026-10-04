package com.risediary.app.service

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real DataStore round trips, isolated from the application's timer file. */
class TimerSessionStoreRecoveryTest {
    private fun store(boot: Int?, now: Long): TimerSessionStore {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "timer-review-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory.apply { mkdirs() }
        }
        return TimerSessionStore(isolated, BootIdentityProvider { boot },
            object : ElapsedRealtimeClock { override fun millis() = now },
            Clock.fixed(Instant.ofEpochMilli(9_999_999L), ZoneOffset.UTC))
    }

    @Test fun oldLimitFileUpgradesAtomicallyToFinishedAndStaysFinished() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = {
            File(base.cacheDir, "old-timer-${UUID.randomUUID()}.preferences_pb")
        })
        try {
            data.edit {
                it[stringPreferencesKey("status")] = "LIMIT_REACHED"
                it[longPreferencesKey("started_at_epoch")] = 123_000L
                it[longPreferencesKey("elapsed_millis")] = 7_200_000L
                it[intPreferencesKey("notified_milestones")] = 15
            }
            val store = TimerSessionStore(data, BootIdentityProvider { 5 },
                object : ElapsedRealtimeClock { override fun millis() = 50_000_000L },
                Clock.fixed(Instant.ofEpochMilli(9_999_999L), ZoneOffset.UTC))
            val restored = store.load()
            assertEquals(TimerStatus.FINISHED, restored.status)
            assertEquals(7_200_000L, restored.elapsedMillis)
            assertEquals(7, restored.notifiedMilestonesMask)
            val persisted = data.data.first()
            assertEquals(2, persisted[intPreferencesKey("duration_policy_version")])
            assertEquals("FINISHED", persisted[stringPreferencesKey("status")])
            assertEquals(restored, store.load())
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test fun persistedDifferentBootWithGreaterUptimeReturnsPaused() = runBlocking {
        val store = store(5, 1_200_000L)
        store.save(TimerSession(TimerStatus.RUNNING, elapsedMillis = 300_000L,
            resumedAtElapsedRealtime = 600_000L, bootCount = 4))
        val restored = store.load()
        assertEquals(TimerStatus.PAUSED, restored.status)
        assertEquals(300_000L, restored.elapsedMillis)
        assertEquals(0L, restored.resumedAtElapsedRealtime)
    }

    @Test fun terminalServicePipelinePersistsBeforeItsStopCallback() = runBlocking {
        val store = store(5, 1_200_000L)
        var stops = 0
        var published: TimerSession? = null
        val committer = TimerSessionCommitter(
            save = store::save, publish = { published = it }, notify = {},
            stop = {
                val saved = runBlocking { store.load() }
                assertEquals(TimerStatus.LIMIT_REACHED, saved.status)
                assertEquals(TimerMilestones.maskThrough(TimerMath.MAX_DURATION_MILLIS), saved.notifiedMilestonesMask)
                stops++
            }
        )
        committer.commit(prepareTimerTransition(TimerSession(TimerStatus.PAUSED,
            elapsedMillis = TimerMath.MAX_DURATION_MILLIS, bootCount = 5)), persist = true)
        assertEquals(TimerStatus.LIMIT_REACHED, published?.status)
        assertEquals(1, stops)
    }
}