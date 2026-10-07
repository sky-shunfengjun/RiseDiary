package com.risediary.app.service

import org.junit.Assert.*
import org.junit.Test

class TimerSessionPolicyTest {
    @Test fun discardClearsOnlyTheRequestedTimerAndItsVideo() {
        for (status in listOf(TimerStatus.RUNNING, TimerStatus.PAUSED, TimerStatus.LIMIT_REACHED)) {
            val current = TimerSession(status = status, sessionId = "old",
                elapsedMillis = 8_000L, notifiedMilestonesMask = 3,
                kind = TimerKind.VIDEO,
                video = com.risediary.app.media.VideoPlaybackSnapshot(
                    com.risediary.app.media.LocalVideoRef("content://video/1", "video", "video/mp4")))
            val discarded = TimerSessionPolicy.discard(current, "old")
            assertEquals(TimerStatus.IDLE, discarded.status)
            assertNull(discarded.sessionId)
            assertNull(discarded.video)
            assertNull(discarded.finishCandidate)
            assertEquals(0L, discarded.elapsedMillis)
        }
    }

    @Test fun staleDiscardDoesNotStopADifferentSession() {
        val current = TimerSession(status = TimerStatus.RUNNING, sessionId = "new")
        for (id in listOf(null, "", "old")) {
            assertSame(current, TimerSessionPolicy.discard(current, id))
        }
    }

    @Test fun repeatedStartDoesNotResetBeginning() {
        val current = TimerSession(status = TimerStatus.RUNNING, sessionId = "same",
            startedAtEpochMillis = 100_000L, elapsedMillis = 8_000L)
        val next = TimerSessionPolicy.start(current, TimerStartRequest("same", TimerKind.NORMAL),
            200_000L, 20_000L, 1)
        assertEquals(current, next)
    }

    @Test fun otherActiveSessionAndUnfinishedTerminalSessionBlockStart() {
        for (status in listOf(TimerStatus.RUNNING, TimerStatus.PAUSED, TimerStatus.FINISHED, TimerStatus.LIMIT_REACHED)) {
            val current = TimerSession(status = status, sessionId = "old")
            assertTrue(runCatching {
                TimerSessionPolicy.start(current, TimerStartRequest("new", TimerKind.NORMAL),
                    200_000L, 20_000L, 1)
            }.isFailure)
        }
    }

    @Test fun freshTimerUsesRequestedIdentityAndBothClocks() {
        val session = TimerSessionPolicy.start(TimerSession(), TimerStartRequest("new", TimerKind.NORMAL),
            200_000L, 20_000L, 3)
        assertEquals("new", session.sessionId)
        assertEquals(TimerStatus.RUNNING, session.status)
        assertEquals(200_000L, session.startedAtEpochMillis)
        assertEquals(20_000L, session.resumedAtElapsedRealtime)
        assertEquals(3, session.bootCount)
    }
}
