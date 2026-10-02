package com.risediary.app.ui.timer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TimerActionDock(
    session: TimerSession,
    backdrop: Backdrop,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    onRecord: () -> Unit
) {
    val active = session.isActive
    val finished = session.status == TimerStatus.FINISHED
    val colors = MiuixTheme.colorScheme
    val leftText = when (session.status) {
        TimerStatus.IDLE -> R.string.mode_select_timer
        TimerStatus.RUNNING -> R.string.timer_pause
        TimerStatus.PAUSED -> R.string.timer_resume
        TimerStatus.FINISHED -> R.string.timer_restart
        TimerStatus.LIMIT_REACHED -> R.string.timer_fill_record
    }
    val leftIcon = when (session.status) {
        TimerStatus.IDLE, TimerStatus.PAUSED -> AppIcons.PlayArrow
        TimerStatus.RUNNING -> AppIcons.Pause
        TimerStatus.FINISHED -> AppIcons.Refresh
        TimerStatus.LIMIT_REACHED -> AppIcons.EditNote
    }
    val leftAction = when (session.status) {
        TimerStatus.IDLE -> onStart
        TimerStatus.RUNNING -> onPause
        TimerStatus.PAUSED -> onResume
        TimerStatus.FINISHED -> onReset
        TimerStatus.LIMIT_REACHED -> onRecord
    }
    // Keep each visible position at one call site so status changes retain its press spring.
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassButton(
            onClick = leftAction,
            backdrop = backdrop,
            modifier = Modifier.width(if (finished) 132.dp else if (active) 176.dp else 204.dp),
            tint = if (finished) Color.Unspecified else colors.primary.copy(alpha = 0.075f),
            height = 58.dp,
            highlightIntensity = if (finished) 0.35f else 0.38f,
            highlightRadiusMultiplier = if (finished) 0.95f else 1f,
            pressExpansion = 2.dp
        ) {
            Icon(leftIcon, null, Modifier.size(if (finished) 22.dp else 24.dp))
            Text(
                stringResource(leftText),
                fontSize = if (finished) TextUnit.Unspecified else MiuixTheme.textStyles.title4.fontSize,
                fontWeight = if (finished) FontWeight.Medium else FontWeight.SemiBold
            )
        }
        if (active || finished) {
            LiquidGlassButton(
                onClick = if (active) onFinish else onRecord,
                backdrop = backdrop,
                modifier = Modifier.width(if (active) 60.dp else 176.dp),
                tint = if (active) colors.error.copy(alpha = 0.06f) else colors.primary.copy(alpha = 0.075f),
                height = if (active) 60.dp else 58.dp,
                horizontalPadding = if (active) 0.dp else 16.dp,
                highlightIntensity = if (active) 0.35f else 0.38f,
                highlightRadiusMultiplier = if (active) 0.95f else 1f,
                pressExpansion = 2.dp
            ) {
                if (active) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.Stop,
                            contentDescription = stringResource(R.string.timer_end_timing),
                            tint = colors.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(AppIcons.EditNote, null, Modifier.size(22.dp))
                    Text(
                        stringResource(R.string.timer_fill_record),
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
