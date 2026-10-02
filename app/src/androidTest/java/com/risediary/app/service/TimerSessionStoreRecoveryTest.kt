package com.risediary.app.service

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
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