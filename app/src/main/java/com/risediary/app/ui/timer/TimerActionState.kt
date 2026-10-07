package com.risediary.app.ui.timer

import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus

internal enum class TimerActionState { START, PAUSE, CONTINUE_FINISH, CONFIRMING, OPENING, RETRY_OPENING }

internal fun timerActionState(
    session: TimerSession,
    transferring: Boolean = false,
    transferFailed: Boolean = false
): TimerActionState = when {
    transferFailed -> TimerActionState.RETRY_OPENING
    session.finishCandidate != null -> TimerActionState.CONFIRMING
    transferring || session.isTerminal -> TimerActionState.OPENING
    session.status == TimerStatus.RUNNING -> TimerActionState.PAUSE
    session.status == TimerStatus.PAUSED -> TimerActionState.CONTINUE_FINISH
    else -> TimerActionState.START
}
