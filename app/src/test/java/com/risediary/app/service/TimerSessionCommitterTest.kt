package com.risediary.app.service

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimerSessionCommitterTest {
    @Test fun limitWaitsForDurableSaveBeforePublishNotifyAndStop() = runTest {
        val saveBarrier = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val committer = TimerSessionCommitter(
            save = { events.add("save-start"); saveBarrier.await(); events.add("saved") },
            publish = { events.add("publish") }, notify = { events.add("notify") }, stop = { events.add("stop") }
        )
        val transition = prepareTimerTransition(TimerSession(TimerStatus.PAUSED,
            elapsedMillis = TimerMath.MAX_DURATION_MILLIS))
        val job = launch { committer.commit(transition, persist = true) }
        runCurrent()
        assertEquals(listOf("save-start"), events)
        saveBarrier.complete(Unit); job.join()
        assertEquals(listOf("save-start", "saved", "publish", "notify", "stop"), events)
    }

    @Test fun saveFailureKeepsTerminalTransitionRetryableAndDoesNotStop() = runTest {
        var failSave = true
        val events = mutableListOf<String>()
        val committer = TimerSessionCommitter(
            save = { if (failSave) throw IOException("disk"); events.add("saved") },
            publish = { events.add("publish") }, notify = { events.add("notify") }, stop = { events.add("stop") }
        )
        val transition = prepareTimerTransition(TimerSession(TimerStatus.RUNNING,
            elapsedMillis = TimerMath.MAX_DURATION_MILLIS))
        assertTrue(runCatching { committer.commit(transition, persist = true) }.isFailure)
        assertTrue(events.isEmpty())
        failSave = false
        committer.commit(transition, persist = true)
        assertEquals(listOf("saved", "publish", "notify", "stop"), events)
    }

    @Test fun restoredLimitWithPersistedMaskDoesNotNotifyAgain() = runTest {
        var notifications = 0
        var saves = 0
        var stops = 0
        val committer = TimerSessionCommitter(save = { saves++ }, publish = {},
            notify = { notifications++ }, stop = { stops++ })
        val transition = prepareTimerTransition(TimerSession(TimerStatus.LIMIT_REACHED,
            elapsedMillis = TimerMath.MAX_DURATION_MILLIS,
            notifiedMilestonesMask = TimerMilestones.maskThrough(TimerMath.MAX_DURATION_MILLIS)))
        committer.commit(transition, persist = false)
        assertEquals(1, saves)
        assertEquals(0, notifications)
        assertEquals(1, stops)
    }

    @Test fun checkpointsRespectFifteenSecondsAndMilestonesPersistImmediately() {
        val running = TimerSession(TimerStatus.RUNNING, elapsedMillis = 10_000L)
        assertFalse(shouldPersistTimerTick(running, running, 14_999L, 0L))
        assertTrue(shouldPersistTimerTick(running, running, 15_000L, 0L))
        assertTrue(shouldPersistTimerTick(running,
            running.copy(notifiedMilestonesMask = TimerMilestone.THIRTY.bit), 1L, 0L))
        assertTrue(shouldPersistTimerTick(running,
            running.copy(status = TimerStatus.LIMIT_REACHED), 1L, 0L))
    }
}