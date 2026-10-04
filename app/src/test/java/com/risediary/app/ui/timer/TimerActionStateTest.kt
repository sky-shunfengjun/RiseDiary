package com.risediary.app.ui.timer

import com.risediary.app.service.*
import org.junit.Assert.*
import org.junit.Test

class TimerActionStateTest {
    @Test fun normalAndVideoTimersRequirePauseBeforeOfferingFinish() {
        for (kind in TimerKind.entries) {
            val running = TimerSession(status = TimerStatus.RUNNING, kind = kind)
            assertEquals(TimerActionState.PAUSE, timerActionState(running))
            assertEquals(TimerActionState.CONTINUE_FINISH,
                timerActionState(running.copy(status = TimerStatus.PAUSED)))
        }
    }

    @Test fun confirmedFinishNeverOffersTheOldRecordOrRestartActions() {
        assertEquals(TimerActionState.OPENING,
            timerActionState(TimerSession(status = TimerStatus.FINISHED)))
        assertEquals(TimerActionState.OPENING,
            timerActionState(TimerSession(status = TimerStatus.IDLE), transferring = true))
        assertEquals(TimerActionState.RETRY_OPENING,
            timerActionState(TimerSession(status = TimerStatus.FINISHED), transferFailed = true))
    }

    @Test fun aPendingFinishCandidateBlocksContinueAndFinishWhileTheDialogOwnsTheAction() {
        val session = TimerSession(status = TimerStatus.PAUSED, sessionId = "s",
            finishCandidate = TimerFinishCandidate("s", 100_000L, 8_000L, TimerStatus.PAUSED))
        assertEquals(TimerActionState.CONFIRMING, timerActionState(session))
    }
}
