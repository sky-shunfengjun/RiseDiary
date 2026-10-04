package com.risediary.app.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import com.risediary.app.ui.components.PageBackHandler as BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.service.TimerMath
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.components.SecondaryPageScaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TimerScreen(
    viewModel: TimerViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val liveSession by viewModel.session.collectAsStateWithLifecycle()
    val session = viewModel.discardDisplaySession ?: viewModel.handoffSession ?: liveSession
    val persistenceError by viewModel.persistenceError.collectAsStateWithLifecycle()
    val commandError by viewModel.commandError.collectAsStateWithLifecycle()
    var showLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    var leaveSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel.nextRoute) {
        viewModel.nextRoute?.let {
            showLeaveConfirm = false
            navigator.replace(it)
            viewModel.consumeRoute()
        }
    }
    LaunchedEffect(viewModel.discardComplete) {
        if (viewModel.discardComplete) {
            showLeaveConfirm = false
            navigator.pop()
            viewModel.consumeDiscard()
        }
    }

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


    BackHandler(enabled = session.isActive || viewModel.busy || viewModel.finishing || viewModel.discarding) {
        if (!viewModel.finishing && !viewModel.busy && !viewModel.discarding) {
            leaveSessionId = viewModel.session.value.sessionId
            showLeaveConfirm = true
        }
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
            if (viewModel.finishing || viewModel.busy || viewModel.discarding) Unit
            else if (viewModel.session.value.isActive) {
                leaveSessionId = viewModel.session.value.sessionId
                showLeaveConfirm = true
            }
            else navigator.pop()
        },
        bottomAction = { backdrop ->
            TimerActionDock(
                session = session,
                backdrop = backdrop,
                onStart = ::startTimer,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onFinish = viewModel::requestFinish,
                onRetry = viewModel::openRecord,
                transferring = viewModel.finishing,
                transferFailed = viewModel.transferFailed,
                enabled = !viewModel.busy && !viewModel.discarding && (!persistenceError || viewModel.transferFailed)
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
            val problem = viewModel.error ?: commandError
            if (problem != null) {
                Column(Modifier.align(Alignment.TopCenter)) { Text(problem, color = MiuixTheme.colorScheme.error) }
            }
            if (persistenceError) {
                Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.timer_error_save), color = MiuixTheme.colorScheme.error)
                    TextButton(text = stringResource(R.string.action_retry), onClick = viewModel::retryPersistence)
                }
            }
            TimerInstrument(
                session = session,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        }
    }

    TimerFinishDialogs(session, persistenceError, viewModel)

    if (showLeaveConfirm) {
        TimerLeaveDialog(liveSession, leaveSessionId, viewModel,
            onLeave = { showLeaveConfirm = false; navigator.pop() },
            onDismiss = { showLeaveConfirm = false })
    }
}
