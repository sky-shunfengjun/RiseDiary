package com.risediary.app.ui.timer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.util.formatTimerClock
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TimerInstrument(
    session: TimerSession,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fullScreen: Boolean = false,
    dense: Boolean = false
) {
    val colors = MiuixTheme.colorScheme
    val clockShadow = if (fullScreen) Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f) else null
    val statusText = stringResource(when (session.status) {
        TimerStatus.IDLE -> R.string.timer_status_idle
        TimerStatus.RUNNING -> R.string.timer_status_running
        TimerStatus.PAUSED -> R.string.notification_timer_paused_title
        TimerStatus.FINISHED -> R.string.timer_status_finished
        TimerStatus.LIMIT_REACHED -> R.string.notification_timer_limit_title
    })
    val statusColor = when {
        fullScreen -> Color.White.copy(alpha = 0.78f)
        session.status == TimerStatus.RUNNING -> colors.primary
        session.status == TimerStatus.LIMIT_REACHED -> colors.error
        else -> colors.onSurfaceVariantSummary
    }
    BoxWithConstraints(modifier.padding(horizontal = if (compact) 0.dp else 8.dp)) {
        // Digit cells include the user's font scale so the whole clock fits without clipping.
        val fontScale = LocalDensity.current.fontScale
        val cap = if (compact) 22f else if (dense) 32f else 52f
        val digitSize = minOf(cap, maxWidth.value * 52f / 258f / fontScale).coerceAtLeast(1f).sp
        Column(Modifier.fillMaxWidth(),
            horizontalAlignment = if (compact) Alignment.Start else Alignment.CenterHorizontally) {
            Text(statusText, style = MiuixTheme.textStyles.body1.copy(shadow = clockShadow),
                modifier = if (compact) Modifier else Modifier.clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.09f)).padding(horizontal = if (dense) 10.dp else 14.dp, vertical = if (dense) 4.dp else 7.dp),
                color = statusColor, fontSize = if (compact || dense) 12.sp else MiuixTheme.textStyles.headline2.fontSize,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(if (compact) 3.dp else if (dense) 10.dp else 26.dp))
            RollingTimerDigits(formatTimerClock(session.elapsedMillis), digitSize,
                if (fullScreen) Color.White else colors.onSurface, statusColor, clockShadow)
            if (!compact) {
                Spacer(Modifier.height(if (dense) 10.dp else 28.dp))
                MinuteSecondTrack(((session.elapsedMillis / 1_000L) % 60L).toInt(),
                    Modifier.fillMaxWidth(), if (dense) 24.dp else 48.dp)
                if (!dense) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.timer_track_caption),
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = colors.onSurfaceVariantSummary.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
internal fun RollingTimerDigits(
    value: String,
    fontSize: TextUnit = 52.sp,
    foreground: Color = MiuixTheme.colorScheme.onSurface,
    separator: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    shadow: Shadow? = null
) {
    val cellScale = fontSize.value / 52f * LocalDensity.current.fontScale
    val style = timerDigitStyle(fontSize).copy(shadow = shadow)
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        value.forEachIndexed { index, character ->
            if (character == ':') {
                Text(":", modifier = Modifier.width((15f * cellScale).dp), style = style,
                    textAlign = TextAlign.Center, color = separator)
            } else {
                AnimatedContent(
                    targetState = character,
                    modifier = Modifier.width((38f * cellScale).dp),
                    transitionSpec = {
                        (slideInVertically(tween(120)) { height -> height / 3 } + fadeIn(tween(90))) togetherWith
                            (slideOutVertically(tween(120)) { height -> -height / 3 } + fadeOut(tween(90))) using
                            SizeTransform(clip = true)
                    },
                    contentAlignment = Alignment.Center,
                    label = "timer_digit_" + index
                ) { digit ->
                    Text(digit.toString(), style = style, color = foreground,
                        textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun timerDigitStyle(fontSize: TextUnit = 52.sp): TextStyle =
    MiuixTheme.textStyles.title1.copy(
        fontSize = fontSize,
        lineHeight = (fontSize.value * 60f / 52f).sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontFeatureSettings = "tnum"
    )

@Composable
internal fun MinuteSecondTrack(
    second: Int,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 48.dp
) {
    val animatedSecond by animateFloatAsState(
        targetValue = second.coerceIn(0, 59).toFloat(),
        animationSpec = if (second == 0) snap() else tween(180),
        label = "second_track"
    )
    val primary = MiuixTheme.colorScheme.primary
    val track = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val minor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Canvas(modifier = modifier.height(trackHeight)) {
        val inset = 8.dp.toPx()
        val availableWidth = size.width - inset * 2
        val baseY = size.height * 0.68f
        drawLine(
            color = track,
            start = androidx.compose.ui.geometry.Offset(inset, baseY),
            end = androidx.compose.ui.geometry.Offset(size.width - inset, baseY),
            strokeWidth = 1.dp.toPx()
        )
        repeat(60) { index ->
            val x = inset + availableWidth * index / 59f
            val isMajor = index % 5 == 0
            val tickHeight = if (isMajor) 13.dp.toPx() else 6.dp.toPx()
            drawLine(
                color = if (isMajor) minor.copy(alpha = 0.52f) else minor,
                start = androidx.compose.ui.geometry.Offset(x, baseY - tickHeight / 2),
                end = androidx.compose.ui.geometry.Offset(x, baseY + tickHeight / 2),
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
            )
        }
        val cursorX = inset + availableWidth * animatedSecond / 59f
        drawCircle(
            color = primary.copy(alpha = 0.18f),
            radius = 7.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cursorX, baseY)
        )
        drawCircle(
            color = primary,
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cursorX, baseY)
        )
    }
}
