package com.risediary.app.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.kyant.backdrop.Backdrop
import com.risediary.app.service.TimerMath
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import com.risediary.app.util.formatTimerClock
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

@Composable
fun TimerScreen(
    viewModel: TimerViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.start()
    }

    fun startTimer() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    fun openRecord() {
        val duration = session.elapsedMillis
        val startTime = session.startedAtEpochMillis
        navigator.push(
            Route.RecordForm(
                isTimer = true,
                duration = duration,
                startTime = startTime
            )
        )
    }

    BackHandler(enabled = session.isActive) {
        showLeaveConfirm = true
    }

    val isRunning = session.status == TimerStatus.RUNNING
    val window = context.findActivity()?.window
    DisposableEffect(isRunning, window) {
        if (isRunning) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    SecondaryPageScaffold(
        title = stringResource(R.string.timer_title),
        onBack = {
            if (session.isActive) showLeaveConfirm = true
            else navigator.pop()
        },
        bottomAction = { backdrop ->
            TimerActionDock(
                session = session,
                backdrop = backdrop,
                onStart = ::startTimer,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onFinish = { showFinishConfirm = true },
                onReset = viewModel::reset,
                onRecord = ::openRecord
            )
        }
    ) { innerPadding ->
        val ambientProgress =
            (session.elapsedMillis / TimerMath.MAX_DURATION_MILLIS.toFloat())
                .coerceIn(0f, 1f)
        val ambientColor by animateColorAsState(
            targetValue = lerp(
                MiuixTheme.colorScheme.primary,
                Color(0xFFF0A05A),
                ambientProgress * 0.45f
            ),
            animationSpec = tween(900),
            label = "timer_ambient_color"
        )
        val glowBrush = Brush.radialGradient(
            colors = listOf(
                ambientColor.copy(alpha = 0.12f),
                ambientColor.copy(alpha = 0.04f),
                Color.Transparent
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(glowBrush)
        ) {
            TimerInstrument(
                session = session,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        }
    }

    if (showFinishConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text(stringResource(R.string.timer_end_timing)) },
            text = { Text(stringResource(R.string.timer_finish_dialog_message)) },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.timer_confirm_end),
                    onClick = {
                        showFinishConfirm = false
                        viewModel.finish()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        disabledColor = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                        disabledTextColor = MiuixTheme.colorScheme.error
                    )
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(R.string.timer_continue_timing),
                    onClick = { showFinishConfirm = false },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }

    if (showLeaveConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.timer_leave_dialog_title)) },
            text = { Text(stringResource(R.string.timer_leave_dialog_message)) },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.timer_leave_confirm),
                    onClick = {
                        showLeaveConfirm = false
                        navigator.pop()
                    },
                    colors = liquidDialogConfirmButtonColors()
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(R.string.timer_leave_cancel),
                    onClick = { showLeaveConfirm = false },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}

@Composable
private fun TimerInstrument(
    session: TimerSession,
    modifier: Modifier = Modifier
) {
    val statusText = when (session.status) {
        TimerStatus.IDLE -> stringResource(R.string.timer_status_idle)
        TimerStatus.RUNNING -> stringResource(R.string.timer_status_running)
        TimerStatus.PAUSED -> stringResource(R.string.notification_timer_paused_title)
        TimerStatus.FINISHED -> stringResource(R.string.timer_status_finished)
        TimerStatus.LIMIT_REACHED -> stringResource(R.string.notification_timer_limit_title)
    }
    val statusColor = when (session.status) {
        TimerStatus.RUNNING -> MiuixTheme.colorScheme.primary
        TimerStatus.LIMIT_REACHED -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusText,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(statusColor.copy(alpha = 0.09f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = statusColor,
            fontSize = MiuixTheme.textStyles.headline2.fontSize,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(26.dp))
        RollingTimerDigits(formatTimerClock(session.elapsedMillis))
        Spacer(modifier = Modifier.height(28.dp))
        MinuteSecondTrack(
            second = ((session.elapsedMillis / 1_000L) % 60L).toInt(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.timer_track_caption),
            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun RollingTimerDigits(value: String) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        value.forEachIndexed { index, character ->
            if (character == ':') {
                Text(
                    text = ":",
                    modifier = Modifier.width(15.dp),
                    style = timerDigitStyle(),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                AnimatedContent(
                    targetState = character,
                    modifier = Modifier.width(38.dp),
                    transitionSpec = {
                        (
                            slideInVertically(tween(120)) { height -> height / 3 } +
                                fadeIn(tween(90))
                            ) togetherWith (
                            slideOutVertically(tween(120)) { height -> -height / 3 } +
                                fadeOut(tween(90))
                            ) using SizeTransform(clip = true)
                    },
                    contentAlignment = Alignment.Center,
                    label = "timer_digit_$index"
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        style = timerDigitStyle(),
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun timerDigitStyle(): TextStyle =
    MiuixTheme.textStyles.title1.copy(
        fontSize = 52.sp,
        lineHeight = 60.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontFeatureSettings = "tnum"
    )

@Composable
private fun MinuteSecondTrack(
    second: Int,
    modifier: Modifier = Modifier
) {
    val animatedSecond by animateFloatAsState(
        targetValue = second.coerceIn(0, 59).toFloat(),
        animationSpec = if (second == 0) snap() else tween(180),
        label = "second_track"
    )
    val primary = MiuixTheme.colorScheme.primary
    val track = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val minor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Canvas(modifier = modifier.height(48.dp)) {
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

@Composable
private fun TimerActionDock(
    session: TimerSession,
    backdrop: Backdrop,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    onRecord: () -> Unit
) {
    when (session.status) {
        TimerStatus.IDLE -> {
            PrimaryTimerButton(
                text = stringResource(R.string.mode_select_timer),
                icon = { Icon(AppIcons.PlayArrow, null, Modifier.size(24.dp)) },
                onClick = onStart,
                backdrop = backdrop
            )
        }

        TimerStatus.RUNNING, TimerStatus.PAUSED -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryTimerButton(
                    text = if (session.status == TimerStatus.PAUSED) {
                        stringResource(R.string.timer_resume)
                    } else {
                        stringResource(R.string.timer_pause)
                    },
                    icon = {
                        Icon(
                            if (session.status == TimerStatus.PAUSED) {
                                AppIcons.PlayArrow
                            } else {
                                AppIcons.Pause
                            },
                            null,
                            Modifier.size(24.dp)
                        )
                    },
                    onClick = if (session.status == TimerStatus.PAUSED) onResume else onPause,
                    backdrop = backdrop,
                    width = 176.dp
                )
                LiquidGlassButton(
                    onClick = onFinish,
                    backdrop = backdrop,
                    modifier = Modifier.size(60.dp),
                    tint = MiuixTheme.colorScheme.error.copy(alpha = 0.06f),
                    height = 60.dp,
                    horizontalPadding = 0.dp,
                    highlightIntensity = 0.35f,
                    highlightRadiusMultiplier = 0.95f,
                    pressExpansion = 2.dp
                ) {
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.Stop,
                            contentDescription = stringResource(R.string.timer_end_timing),
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        TimerStatus.FINISHED -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassButton(
                    onClick = onReset,
                    backdrop = backdrop,
                    modifier = Modifier.width(132.dp),
                    height = 58.dp,
                    highlightIntensity = 0.35f,
                    highlightRadiusMultiplier = 0.95f,
                    pressExpansion = 2.dp
                ) {
                    Icon(AppIcons.Refresh, null, Modifier.size(22.dp))
                    Text(stringResource(R.string.timer_restart), fontWeight = FontWeight.Medium)
                }
                PrimaryTimerButton(
                    text = stringResource(R.string.timer_fill_record),
                    icon = { Icon(AppIcons.EditNote, null, Modifier.size(22.dp)) },
                    onClick = onRecord,
                    backdrop = backdrop,
                    width = 176.dp
                )
            }
        }

        TimerStatus.LIMIT_REACHED -> {
            PrimaryTimerButton(
                text = stringResource(R.string.timer_fill_record),
                icon = { Icon(AppIcons.EditNote, null, Modifier.size(24.dp)) },
                onClick = onRecord,
                backdrop = backdrop
            )
        }
    }
}
