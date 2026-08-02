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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kyant.backdrop.Backdrop
import com.risediary.app.service.TimerMath
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.Screen
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.util.formatTimerClock

@Composable
fun TimerScreen(
    navController: NavController,
    viewModel: TimerViewModel = hiltViewModel()
) {
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
        navController.navigate(
            Screen.RecordForm.createRoute(
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
    DisposableEffect(isRunning) {
        if (isRunning) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    SecondaryPageScaffold(
        title = "计时",
        onBack = {
            if (session.isActive) showLeaveConfirm = true
            else navController.navigateUp()
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
                MaterialTheme.colorScheme.primary,
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
            title = { Text("结束计时") },
            text = { Text("结束后可以继续填写本次记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirm = false
                        viewModel.finish()
                    }
                ) {
                    Text("确认结束", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) {
                    Text("继续计时")
                }
            }
        )
    }

    if (showLeaveConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("离开计时页面？") },
            text = { Text("计时会在后台继续，你可以从应用或通知栏返回。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        navController.popBackStack()
                    }
                ) {
                    Text("后台继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("留在这里")
                }
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
        TimerStatus.IDLE -> "准备开始"
        TimerStatus.RUNNING -> "正在计时"
        TimerStatus.PAUSED -> "计时已暂停"
        TimerStatus.FINISHED -> "计时已结束"
        TimerStatus.LIMIT_REACHED -> "已达到 120 分钟上限"
    }
    val statusColor = when (session.status) {
        TimerStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TimerStatus.LIMIT_REACHED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
            style = MaterialTheme.typography.labelLarge,
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
            text = "当前分钟 · 最长 120 分钟",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurface,
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
    MaterialTheme.typography.displayLarge.copy(
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
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val minor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

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
                text = "开始计时",
                icon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp)) },
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
                    text = if (session.status == TimerStatus.PAUSED) "继续" else "暂停",
                    icon = {
                        Icon(
                            if (session.status == TimerStatus.PAUSED) {
                                Icons.Default.PlayArrow
                            } else {
                                Icons.Default.Pause
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
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
                    height = 60.dp,
                    horizontalPadding = 0.dp,
                    highlightIntensity = 0.35f,
                    highlightRadiusMultiplier = 0.95f,
                    pressExpansion = 2.dp
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "结束计时",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
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
                    Icon(Icons.Default.Refresh, null, Modifier.size(22.dp))
                    Text("重新计时", fontWeight = FontWeight.Medium)
                }
                PrimaryTimerButton(
                    text = "填写记录",
                    icon = { Icon(Icons.Default.EditNote, null, Modifier.size(22.dp)) },
                    onClick = onRecord,
                    backdrop = backdrop,
                    width = 176.dp
                )
            }
        }

        TimerStatus.LIMIT_REACHED -> {
            PrimaryTimerButton(
                text = "填写记录",
                icon = { Icon(Icons.Default.EditNote, null, Modifier.size(24.dp)) },
                onClick = onRecord,
                backdrop = backdrop
            )
        }
    }
}
