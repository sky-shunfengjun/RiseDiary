package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.timer.TimerActionState
import com.risediary.app.ui.timer.timerActionState
import com.risediary.app.ui.video.VideoGlassButton

@Composable
internal fun LibraryTimerControls(session: TimerSession?, starting: Boolean, backdrop: Backdrop,
    onStart: () -> Unit, onFinish: () -> Unit, onRetry: () -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier, transferring: Boolean = false, transferFailed: Boolean = false, buttonSize: Dp = 60.dp) {
    if (session == null) VideoGlassButton(onStart, backdrop, icon = AppIcons.Schedule,
            description = if (starting) "正在开始…" else "开始计时", fullScreen = true,
            enabled = enabled && !starting, accent = true,
            modifier = modifier.size(buttonSize).testTag("library-timer-controls"))
    else {
        val action = timerActionState(session, transferring, transferFailed)
        val retry = action == TimerActionState.RETRY_OPENING
        val canFinish = action == TimerActionState.PAUSE || action == TimerActionState.CONTINUE_FINISH
        VideoGlassButton(if (retry) onRetry else onFinish, backdrop,
            icon = if (retry) AppIcons.Refresh else AppIcons.FlightTakeoffLite,
            description = if (retry) stringResource(R.string.action_retry) else stringResource(R.string.video_timer_finish),
            fullScreen = true, accent = true, enabled = enabled && (retry || canFinish),
            modifier = modifier.size(buttonSize).testTag("library-timer-controls"))
    }
}
