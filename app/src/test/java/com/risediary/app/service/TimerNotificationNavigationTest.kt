package com.risediary.app.service

import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.ui.navigation3.Route
import org.junit.Assert.*
import org.junit.Test

class TimerNotificationNavigationTest {
    private val entry = TimerNotificationEntry("a", "tap")
    private val running = TimerSession(status = TimerStatus.RUNNING, sessionId = "a")

    @Test fun ordinaryNotificationReturnsToExistingTimerRoute() {
        assertEquals(Route.Timer, resolveTimerNotificationRoute(entry, running, false))
    }
    @Test fun videoNotificationUsesTheActiveIdentity() {
        val video = running.copy(kind = TimerKind.VIDEO,
            video = VideoPlaybackSnapshot(LocalVideoRef("content://provider/video/1", "private.mp4", "video/mp4")))
        assertEquals(Route.VideoTimer("a"), resolveTimerNotificationRoute(entry, video, false))
    }
    @Test fun lockedAppCannotNavigateToAnyPrivateTimer() {
        assertNull(resolveTimerNotificationRoute(entry, running, true))
        assertEquals(Route.Timer, resolveTimerNotificationRoute(entry, running, false))
    }
    @Test fun oldNotificationCannotOpenTheNewTimerOrStartAnotherOne() {
        assertNull(resolveTimerNotificationRoute(entry, running.copy(sessionId = "b"), false))
        assertNull(resolveTimerNotificationRoute(entry, TimerSession(), false))
    }
    @Test fun pausedAndPendingFinishRemainResolvable() {
        assertEquals(Route.Timer, resolveTimerNotificationRoute(entry, running.copy(status = TimerStatus.PAUSED), false))
        val waiting = running.copy(finishCandidate = TimerFinishCandidate("a", 100000L, 8000L, TimerStatus.RUNNING))
        assertEquals(Route.Timer, resolveTimerNotificationRoute(entry, waiting, false))
    }
    @Test fun liveTerminalResultCanReturnButMalformedVideoCannot() {
        assertEquals(Route.Timer, resolveTimerNotificationRoute(entry, running.copy(status = TimerStatus.LIMIT_REACHED), false))
        assertNull(resolveTimerNotificationRoute(entry, running.copy(kind = TimerKind.VIDEO, video = null), false))
    }
}
