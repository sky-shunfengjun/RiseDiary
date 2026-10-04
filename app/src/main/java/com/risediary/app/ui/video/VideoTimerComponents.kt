package com.risediary.app.ui.video

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VideoTimerSummary(
    session: TimerSession,
    fullScreen: Boolean,
    notice: String?,
    problem: String?,
    persistenceError: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (fullScreen) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            com.risediary.app.ui.timer.TimerInstrument(session, compact = true, fullScreen = true)
            TimerProblems(notice, problem, persistenceError, true, backdrop, onRetry)
        }
    } else {
        RiseCard(modifier.fillMaxWidth(), allowContentOverflow = true) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.risediary.app.ui.timer.TimerInstrument(session, Modifier.fillMaxWidth(), dense = true)
                TimerProblems(notice, problem, persistenceError, false, backdrop, onRetry)
            }
        }
    }
}

@Composable
private fun TimerProblems(
    notice: String?,
    problem: String?,
    persistenceError: Boolean,
    fullScreen: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val infoColor = if (fullScreen) Color.White else colors.primary
    notice?.let { Text(it, color = infoColor, fontSize = 12.sp) }
    problem?.let { Text(it, color = if (fullScreen) Color.White else colors.error, fontSize = 12.sp) }
    if (persistenceError) {
        Text(stringResource(R.string.timer_error_save), color = if (fullScreen) Color.White else colors.error, fontSize = 12.sp)
        VideoGlassButton(onRetry, backdrop, icon = AppIcons.Refresh,
            label = stringResource(R.string.action_retry), fullScreen = fullScreen)
    }
}
