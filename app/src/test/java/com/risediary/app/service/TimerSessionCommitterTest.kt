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
    @Test fun finishingOldCommandDoesNotStopTheNewFormHandoffRequest() = runTest {
        var latestDeliveredStart = 1
        var serviceStopped = false
        val stopIds = mutableListOf<Int>()
        // Replace the platform boundary: Android only stops for the newest delivered start ID.
        val stopCompleted: (Int) -> Unit = { id ->
            stopIds += id
            if (id == latestDeliveredStart) serviceStopped = true
        }
        TimerSessionCommitter(
            save = {}, publish = { latestDeliveredStart = 2 }, notify = {},
            stop = stopCompleted, commandStartId = 1
        ).commit(prepareTimerTransition(TimerSession(status = TimerStatus.FINISHED,
            sessionId = "session", elapsedMillis = 8_000L)), true)
        assertEquals(listOf(1), stopIds)
        assertFalse("The just-enqueued form cleanup must still be delivered", serviceStopped)
        TimerSessionCommitter(
            save = {}, publish = {}, notify = {}, stop = stopCompleted, commandStartId = 2
        ).commit(prepareTimerTransition(TimerSession()), true)
        assertEquals(listOf(1, 2), stopIds)
        assertTrue(serviceStopped)
    }

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

    @Test fun runningAtTwoHoursKeepsServiceActiveAndAtTwentyFourHoursStopsOnce() = runTest {
        val events = mutableListOf<String>()
        val committer = TimerSessionCommitter(
            save = { events.add("save:${it.elapsedMillis}") },
            publish = { events.add("publish:${it.status}") },
            notify = { events.add("notify:${it.name}") },
            stop = { events.add("stop") }
        )
        val beforeLimit = prepareTimerTransition(TimerSession(TimerStatus.RUNNING,
            elapsedMillis = 7_200_000L, notifiedMilestonesMask = 7))
        committer.commit(beforeLimit, persist = true)
        assertEquals(listOf("save:7200000", "publish:RUNNING"), events)
        events.clear()
        val atLimit = prepareTimerTransition(beforeLimit.session.copy(elapsedMillis = 86_400_000L))
        committer.commit(atLimit, persist = true)
        assertEquals(listOf("save:86400000", "publish:LIMIT_REACHED", "notify:LIMIT", "stop"), events)
        events.clear()
        committer.commit(prepareTimerTransition(atLimit.session), persist = true)
        assertEquals(listOf("save:86400000", "publish:LIMIT_REACHED", "stop"), events)
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
