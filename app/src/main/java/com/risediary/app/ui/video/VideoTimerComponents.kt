package com.risediary.app.ui.video

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.components.AnimatedUiVisibility
import com.risediary.app.ui.components.InlineStatusContent
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.riseCardBackgroundColor

@Composable
internal fun VideoTimerSummary(
    session: TimerSession,
    fullScreen: Boolean,
    notice: String?,
    problem: String?,
    persistenceError: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sessionFlow: kotlinx.coroutines.flow.StateFlow<TimerSession>? = null
) {
    if (fullScreen) {
        Column(modifier) {
            com.risediary.app.ui.timer.TimerInstrument(session, compact = true, fullScreen = true, sessionFlow = sessionFlow)
            VideoTimerProblems(notice, problem, persistenceError, true, backdrop, onRetry, enabled)
        }
    } else {
        RiseCard(modifier.fillMaxWidth(), allowContentOverflow = true) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                com.risediary.app.ui.timer.TimerInstrument(session, Modifier.fillMaxWidth(), dense = true, sessionFlow = sessionFlow)
                VideoTimerProblems(notice, problem, persistenceError, false, backdrop, onRetry, enabled)
            }
        }
    }
}

@Composable
internal fun VideoTimerProblems(
    notice: String?,
    problem: String?,
    persistenceError: Boolean,
    fullScreen: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit,
    enabled: Boolean = true
) {
    val cardBackground = riseCardBackgroundColor()
    val problems = listOfNotNull(problem,
        if (persistenceError) stringResource(R.string.timer_error_save) else null)
    val presentation = TimerProblemPresentation(notice, problems, persistenceError)
    val visible = !notice.isNullOrBlank() || problems.any { it.isNotBlank() }
    var lastVisible by remember { mutableStateOf(presentation) }
    SideEffect { if (visible) lastVisible = presentation }
    val displayed = if (visible) presentation else lastVisible
    AnimatedUiVisibility(visible = visible) { active ->
        Column(Modifier.padding(top = if (fullScreen) 4.dp else 8.dp),
            horizontalAlignment = if (fullScreen) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displayed.notice?.let {
                InlineStatusContent(listOf(it), backdrop = backdrop, fullScreen = fullScreen, error = false,
                    backgroundColor = cardBackground)
            }
            InlineStatusContent(displayed.problems, backdrop = backdrop,
                onRetry = if (displayed.canRetry && !fullScreen) onRetry else null,
                enabled = active && enabled, fullScreen = fullScreen, backgroundColor = cardBackground)
            if (displayed.canRetry && fullScreen) {
                VideoGlassButton(onRetry, backdrop, icon = AppIcons.Refresh,
                    description = stringResource(R.string.action_retry), fullScreen = true,
                    enabled = active && enabled, dimWhenDisabled = false, modifier = Modifier.size(52.dp))
            }
        }
    }
}

private data class TimerProblemPresentation(
    val notice: String?,
    val problems: List<String>,
    val canRetry: Boolean
)
