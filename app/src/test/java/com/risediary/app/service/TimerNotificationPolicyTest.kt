package com.risediary.app.service

import org.junit.Assert.*
import org.junit.Test

class TimerNotificationPolicyTest {
    private val running = TimerSession(status = TimerStatus.RUNNING, sessionId = "session-a")
    private val capable = TimerNotificationCapabilities(36)

    @Test fun androidSixteenUsesItsNativePromotionApiWithoutAMinorVersionGate() {
        assertFalse(TimerNotificationPolicy.supportsLiveUpdates(35))
        assertTrue(TimerNotificationPolicy.supportsLiveUpdates(36))
        assertTrue(TimerNotificationPolicy.supportsLiveUpdates(37))
    }
    @Test fun androidSixteenPointZeroRequestsPromotionWhenTheSystemAllowsIt() {
        assertTrue(TimerNotificationPolicy.requestPromotion(running, true, capable))
    }
    @Test fun androidSixteenPointZeroStillRespectsSystemAndAppSwitches() {
        val caps = capable
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, caps.copy(promotionAllowed = false)))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, false, caps))
        assertEquals(listOf(TimerNotificationAction.PAUSE, TimerNotificationAction.FINISH),
            TimerNotificationPolicy.actions(running))
    }
    @Test fun activeTimerRequestsPromotionOnlyWhenBothSwitchesAllowIt() {
        assertTrue(TimerNotificationPolicy.requestPromotion(running, true, capable))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, false, capable))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable.copy(promotionAllowed = false)))
    }
    @Test fun deniedNotificationsOrLowChannelUseOrdinaryNotification() {
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable.copy(notificationsAllowed = false)))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable.copy(channelImportance = 0)))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable.copy(channelImportance = 1)))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable.copy(sdkInt = 35)))
    }
    @Test fun userDismissalAndFailedCommandsDoNotRequestPromotion() {
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable, dismissed = true))
        assertFalse(TimerNotificationPolicy.requestPromotion(running, true, capable, commandFailed = true))
    }
    @Test fun waitingConfirmationOrCompletedTimersAreNotPromoted() {
        val candidate = TimerFinishCandidate("session-a", 100000L, 8000L, TimerStatus.RUNNING)
        assertFalse(TimerNotificationPolicy.requestPromotion(running.copy(finishCandidate = candidate), true, capable))
        assertFalse(TimerNotificationPolicy.requestPromotion(running.copy(status = TimerStatus.FINISHED), true, capable))
        assertFalse(TimerNotificationPolicy.requestPromotion(TimerSession(), true, capable))
    }
    @Test fun runningActionsPauseOrFinishAndPausedActionsResumeOrFinish() {
        assertEquals(listOf(TimerNotificationAction.PAUSE, TimerNotificationAction.FINISH),
            TimerNotificationPolicy.actions(running))
        assertEquals(listOf(TimerNotificationAction.RESUME, TimerNotificationAction.FINISH),
            TimerNotificationPolicy.actions(running.copy(status = TimerStatus.PAUSED)))
    }
    @Test fun unfinishedRecoveryAndPendingConfirmationHaveNoStateChangingActions() {
        assertTrue(TimerNotificationPolicy.actions(running, commandFailed = true).isEmpty())
        assertTrue(TimerNotificationPolicy.actions(running.copy(sessionId = null)).isEmpty())
        assertTrue(TimerNotificationPolicy.actions(running.copy(status = TimerStatus.LIMIT_REACHED)).isEmpty())
        assertTrue(TimerNotificationPolicy.actions(running.copy(finishCandidate =
            TimerFinishCandidate("session-a", 100000L, 8000L, TimerStatus.RUNNING))).isEmpty())
    }

    @Test fun pausingAtTheSameSecondStillUpdatesNotificationActions() {
        val before = running.copy(elapsedMillis = 5000L, resumedAtElapsedRealtime = 10000L)
        val after = before.copy(status = TimerStatus.PAUSED, resumedAtElapsedRealtime = 0L)
        assertNotEquals(TimerNotificationPolicy.updateKey(before, true, false),
            TimerNotificationPolicy.updateKey(after, true, false))
    }
    @Test fun ticksDoNotRepostNotificationWhileSystemChronometerIsRunning() {
        val before = running.copy(elapsedMillis = 5000L, resumedAtElapsedRealtime = 10000L)
        val after = TimerMath.advance(before, 17000L, 107000L)
        assertEquals(TimerNotificationPolicy.updateKey(before, true, false),
            TimerNotificationPolicy.updateKey(after, true, false))
        assertNotEquals(TimerNotificationPolicy.updateKey(after, true, false),
            TimerNotificationPolicy.updateKey(after, false, false))
    }
    @Test fun chronometerExcludesTimeSpentPaused() {
        val resumed = running.copy(elapsedMillis = 5000L, resumedAtElapsedRealtime = 30000L)
        assertEquals(125000L, TimerNotificationPolicy.chronometerWhen(resumed, 35000L, 135000L))
    }
    @Test fun pendingFinishDisplaysItsCapturedValueInsteadOfUnlockDelay() {
        val waiting = running.copy(elapsedMillis = 20000L, resumedAtElapsedRealtime = 30000L,
            finishCandidate = TimerFinishCandidate("session-a", 108000L, 8000L, TimerStatus.RUNNING))
        assertEquals(8000L, TimerNotificationPolicy.elapsed(waiting, 50000L))
    }
    @Test fun dismissedSessionStaysSuppressedAcrossResumeAndNextSessionIsFresh() {
        val dismissed = TimerNotificationPolicy.dismiss(running, "session-a")
        assertTrue(dismissed.liveUpdateDismissed)
        assertTrue(dismissed.copy(status = TimerStatus.PAUSED).copy(status = TimerStatus.RUNNING).liveUpdateDismissed)
        assertFalse(TimerSessionPolicy.start(TimerSession(),
            TimerStartRequest("session-b", TimerKind.NORMAL), 100000L, 10000L, 1).liveUpdateDismissed)
    }
    @Test fun staleDismissalCannotSuppressANewerTimer() {
        assertSame(running, TimerNotificationPolicy.dismiss(running, "old"))
        assertSame(running, TimerNotificationPolicy.dismiss(running, null))
        val idle = TimerSession()
        assertSame(idle, TimerNotificationPolicy.dismiss(idle, "session-a"))
    }

}
