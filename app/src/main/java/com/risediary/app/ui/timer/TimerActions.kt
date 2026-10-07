package com.risediary.app.ui.timer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerActionDock(
    session: TimerSession,
    backdrop: Backdrop,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    transferring: Boolean = false,
    transferFailed: Boolean = false,
    enabled: Boolean = true,
    fullScreen: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    iconOnly: Boolean = false
) {
    val state = timerActionState(session, transferring, transferFailed)
    val colors = MiuixTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val foreground = if (fullScreen) Color.White else colors.onSurface
    val leftText = when (state) {
        TimerActionState.START -> stringResource(R.string.mode_select_timer)
        TimerActionState.PAUSE -> stringResource(R.string.timer_pause)
        TimerActionState.CONTINUE_FINISH, TimerActionState.CONFIRMING -> stringResource(R.string.timer_resume)
        TimerActionState.OPENING -> "正在打开…"
        TimerActionState.RETRY_OPENING -> stringResource(R.string.action_retry)
    }
    val leftIcon = when (state) {
        TimerActionState.PAUSE -> AppIcons.Pause
        TimerActionState.OPENING -> AppIcons.EditNote
        TimerActionState.RETRY_OPENING -> AppIcons.Refresh
        else -> AppIcons.PlayArrow
    }
    val leftAction = when (state) {
        TimerActionState.START -> onStart
        TimerActionState.PAUSE -> onPause
        TimerActionState.CONTINUE_FINISH -> onResume
        TimerActionState.RETRY_OPENING -> onRetry
        else -> ({})
    }
    val canAct = enabled && state != TimerActionState.OPENING && state != TimerActionState.CONFIRMING
    val paired = state == TimerActionState.CONTINUE_FINISH || state == TimerActionState.CONFIRMING
    val buttonSurface = if (fullScreen) Color.Black.copy(alpha = 0.22f) else Color.Unspecified
    val finishText = stringResource(R.string.video_timer_finish)
    val buttonHeight = if (iconOnly) 56.dp else if (compact) maxOf(48.dp, (28f * fontScale + 20f).dp) else maxOf(58.dp, (32f * fontScale + 24f).dp)
    BoxWithConstraints(modifier.widthIn(max = 360.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val stacked = !iconOnly && (maxWidth < (if (compact) 252.dp else 320.dp) || fontScale > 1.3f)
        val targetDockWidth = if (iconOnly) minOf(maxWidth, if (paired) 124.dp else 56.dp)
            else if (paired || fontScale > 1.3f) maxWidth else minOf(maxWidth, 204.dp)
        val dockWidth by animateDpAsState(targetDockWidth,
            animationSpec = tween(if (paired) 190 else 150), label = "timer_dock_width")
        val pairProgress by animateFloatAsState(if (paired) 1f else 0f,
            animationSpec = tween(if (paired) 190 else 150), label = "timer_finish_visibility")
        val finishWidth = if (iconOnly) 56.dp else if (stacked) maxWidth else (maxWidth - 12.dp) / 2.15f * 1.15f
        val buttons: @Composable RowScope.() -> Unit = {
            // One call site preserves the left button's spring when state or layout changes.
            LiquidGlassButton(
                onClick = { if (canAct) leftAction() }, backdrop = backdrop,
                modifier = if (iconOnly) Modifier.size(56.dp).semantics { contentDescription = leftText }
                    else Modifier.weight(1f),
                enabled = canAct, isInteractive = canAct,
                tint = if (fullScreen) Color.White.copy(alpha = 0.08f) else colors.primary.copy(alpha = 0.075f),
                surfaceColor = buttonSurface, height = buttonHeight,
                horizontalPadding = if (iconOnly) 0.dp else 16.dp,
                highlightIntensity = 0.38f, highlightRadiusMultiplier = 1f, pressExpansion = 2.dp
            ) {
                Icon(leftIcon, null, Modifier.size(if (compact && !iconOnly) 20.dp else 24.dp), tint = foreground)
                if (!iconOnly) Text(leftText, fontSize = if (compact) 14.sp else 16.sp, color = foreground, fontWeight = FontWeight.SemiBold)
            }
            AnimatedVisibility(
                visible = paired,
                modifier = Modifier.graphicsLayer {
                    alpha = pairProgress
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    clip = false
                }.then(if (!paired) Modifier.clearAndSetSemantics { } else Modifier),
                enter = if (stacked) expandVertically(tween(190), expandFrom = Alignment.Bottom, clip = false)
                    else expandHorizontally(tween(190), expandFrom = Alignment.Start, clip = false),
                exit = if (stacked) shrinkVertically(tween(150), shrinkTowards = Alignment.Bottom, clip = false)
                    else shrinkHorizontally(tween(150), shrinkTowards = Alignment.Start, clip = false)
            ) {
                LiquidGlassButton(
                    onClick = { if (paired && canAct) onFinish() }, backdrop = backdrop,
                    modifier = if (iconOnly) Modifier.size(56.dp).semantics { contentDescription = finishText }
                        else Modifier.width(finishWidth),
                    enabled = paired && canAct, isInteractive = paired && canAct,
                    tint = colors.primary.copy(alpha = 0.075f), surfaceColor = buttonSurface, height = buttonHeight,
                    horizontalPadding = if (iconOnly) 0.dp else 16.dp,
                    highlightIntensity = 0.38f, highlightRadiusMultiplier = 1f, pressExpansion = 2.dp
                ) {
                    Icon(AppIcons.FlightTakeoffLite, null, Modifier.size(if (compact && !iconOnly) 20.dp else 24.dp), tint = foreground)
                    if (!iconOnly) Text(finishText, color = foreground,
                        fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (iconOnly) {
            Row(Modifier.width(dockWidth).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp * pairProgress),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons)
        } else {
            FlowRow(Modifier.width(dockWidth),
                horizontalArrangement = Arrangement.spacedBy(12.dp * pairProgress),
                verticalArrangement = Arrangement.spacedBy(10.dp * pairProgress),
                maxItemsInEachRow = if (stacked) 1 else 2,
                content = buttons)
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
