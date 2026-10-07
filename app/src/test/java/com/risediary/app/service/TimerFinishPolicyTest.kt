package com.risediary.app.service

import org.junit.Assert.*
import org.junit.Test

class TimerFinishPolicyTest {
    private fun running() = TimerSession(status = TimerStatus.RUNNING,
        sessionId = "s1", startedAtEpochMillis = 100_000L, elapsedMillis = 5_000L,
        resumedAtElapsedRealtime = 10_000L, resumedAtWallClock = 105_000L, bootCount = 1)

    @Test fun confirmationUsesButtonTimeAfterDialogWait() {
        val session = running()
        val pending = session.copy(finishCandidate = TimerFinishPolicy.capture(session, 108_000L, 13_000L))
        val finished = TimerFinishPolicy.confirm(pending)
        assertEquals(TimerStatus.FINISHED, finished.status)
        assertEquals(8_000L, finished.elapsedMillis)
        assertEquals(108_000L, finished.endedAtEpochMillis)
        assertNull(finished.finishCandidate)
    }

    @Test fun cancelRunningIncludesDialogWaitFromOriginalBaseline() {
        val session = running()
        val pending = session.copy(finishCandidate = TimerFinishPolicy.capture(session, 108_000L, 13_000L))
        val cancelled = TimerFinishPolicy.cancel(pending, 120_000L, 25_000L)
        assertEquals(TimerStatus.RUNNING, cancelled.status)
        assertEquals(20_000L, cancelled.elapsedMillis)
        assertNull(cancelled.finishCandidate)
    }

    @Test fun cancelPreviouslyPausedKeepsItPaused() {
        val paused = running().copy(status = TimerStatus.PAUSED, resumedAtElapsedRealtime = 0L)
        val pending = paused.copy(finishCandidate = TimerFinishPolicy.capture(paused, 108_000L, 13_000L))
        val cancelled = TimerFinishPolicy.cancel(pending, 120_000L, 25_000L)
        assertEquals(TimerStatus.PAUSED, cancelled.status)
        assertEquals(5_000L, cancelled.elapsedMillis)
    }

    @Test fun repeatedClickKeepsFirstCandidate() {
        val session = running()
        val first = TimerFinishPolicy.capture(session, 108_000L, 13_000L)
        assertEquals(first, TimerFinishPolicy.capture(session.copy(finishCandidate = first), 115_000L, 20_000L))
    }

    @Test fun clickBeforeLimitRemainsValidAfterLaterTickReachesLimit() {
        val session = running().copy(elapsedMillis = 86_399_000L)
        val candidate = TimerFinishPolicy.capture(session, 108_000L, 10_500L)
        val pending = session.copy(finishCandidate = candidate)
        val advanced = prepareTimerTransition(TimerMath.advance(pending, 12_000L, 110_000L)).session
        assertEquals(TimerStatus.RUNNING, advanced.status)
        val finished = TimerFinishPolicy.confirm(advanced)
        assertEquals(TimerStatus.FINISHED, finished.status)
        assertEquals(86_399_500L, finished.elapsedMillis)
        assertEquals(108_000L, finished.endedAtEpochMillis)
        assertEquals(TimerStatus.LIMIT_REACHED, TimerFinishPolicy.cancel(advanced, 111_000L, 13_000L).status)
    }

    @Test fun rebootedPendingTimerDoesNotCountShutdownOnCancel() {
        val session = running()
        val pending = session.copy(finishCandidate = TimerFinishPolicy.capture(session, 108_000L, 13_000L))
        val restored = TimerMath.restore(pending, 2_000L, 999_000L, 2)
        val cancelled = TimerFinishPolicy.cancel(restored, 1_000_000L, 3_000L)
        assertEquals(TimerStatus.PAUSED, cancelled.status)
        assertEquals(5_000L, cancelled.elapsedMillis)
        assertEquals(8_000L, TimerFinishPolicy.confirm(restored).elapsedMillis)
    }

    @Test fun candidateFromOldSessionCannotConfirmNewSession() {
        val candidate = TimerFinishCandidate("old", 108_000L, 8_000L, TimerStatus.RUNNING)
        assertTrue(runCatching { TimerFinishPolicy.confirm(running().copy(finishCandidate = candidate)) }.isFailure)
    }

    @Test fun finishAfterFailedPlaybackCheckpointKeepsVideoAndFirstButtonTime() {
        val snapshot = com.risediary.app.media.VideoPlaybackSnapshot(
            com.risediary.app.media.LocalVideoRef("content://videos/1", "video", "video/mp4"),
            positionMillis = 900L, speed = 2f, loop = true)
        val candidate = TimerFinishPolicy.capture(running(), 108_000L, 13_000L)
        val unpublishedCheckpoint = running().copy(kind = TimerKind.VIDEO, video = snapshot)
        val attached = TimerFinishPolicy.attach(unpublishedCheckpoint, candidate)
        assertEquals(snapshot, attached.video)
        assertEquals(8_000L, TimerFinishPolicy.confirm(attached).elapsedMillis)
        assertEquals(108_000L, TimerFinishPolicy.confirm(attached).endedAtEpochMillis)
    }

    @Test fun laterFinishRequestCannotReplacePendingCandidateOrFinishedRecord() {
        val first = TimerFinishPolicy.capture(running(), 108_000L, 13_000L)
        val attached = TimerFinishPolicy.attach(running(), first)
        val later = first.copy(requestedAtEpochMillis = 120_000L, elapsedMillis = 20_000L)
        assertEquals(attached, TimerFinishPolicy.attach(attached, later))
        val finished = TimerFinishPolicy.confirm(attached)
        assertEquals(finished, TimerFinishPolicy.attach(finished, later))
        assertEquals(TimerSession(), TimerFinishPolicy.attach(TimerSession(), first))
    }

    @Test fun queuedFinishCannotAttachCandidateFromAnotherSession() {
        val old = TimerFinishCandidate("old", 108_000L, 8_000L, TimerStatus.RUNNING)
        assertTrue(runCatching { TimerFinishPolicy.attach(running(), old) }.isFailure)
    }

    @Test fun repeatedConfirmationDoesNotChangeFinalTime() {
        val session = running()
        val finished = TimerFinishPolicy.confirm(session.copy(
            finishCandidate = TimerFinishPolicy.capture(session, 108_000L, 13_000L)))
        assertEquals(finished, TimerFinishPolicy.confirm(finished))
    }
}
