package com.risediary.app.ui.video

import com.risediary.app.ui.projectState
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.risediary.app.service.forControls
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.components.AnimatedUiVisibility
import com.risediary.app.ui.navigation3.*
import com.risediary.app.ui.timer.TimerFinishDialogs
import com.risediary.app.ui.timer.TimerViewModel

import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoTimerScreen(route: Route.VideoTimer,
    vm: VideoTimerViewModel = hiltViewModel(), timerVm: TimerViewModel = hiltViewModel()) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controlsFlow = remember(vm) { vm.session.projectState { it.forControls() } }
    val liveSession by controlsFlow.collectAsStateWithLifecycle()
    val session = timerVm.discardDisplaySession ?: timerVm.handoffSession ?: liveSession
    val started by vm.started.collectAsStateWithLifecycle()
    val starting by vm.starting.collectAsStateWithLifecycle()
    var showLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    val loading by vm.loading.collectAsStateWithLifecycle()
    val selectingVideo by vm.selecting.collectAsStateWithLifecycle()
    val problem by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val playbackError by vm.controller.error.collectAsStateWithLifecycle()
    val videoFlow = remember(vm) { vm.controller.playback.projectState { it?.video } }
    val video by videoFlow.collectAsStateWithLifecycle()
    val persistenceError by timerVm.persistenceError.collectAsStateWithLifecycle()
    val commandError by timerVm.commandError.collectAsStateWithLifecycle()
    val active = LocalPageEffectsActive.current && navigator.current() == route
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val canChooseVideo = active && !started && !starting && !loading && !selectingVideo &&
        !chooserOpen && !timerVm.busy && !timerVm.finishing && !timerVm.discarding
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        chooserOpen = false
        if (result.resultCode == Activity.RESULT_OK) result.data?.let { data ->
            data.data?.let { vm.selectVideo(it, data.flags) }
        }
    }
    fun launchVideoPicker() {
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        launchVideoPicker()
    }
    fun choose() {
        if (!canChooseVideo || chooserOpen || vm.loading.value || vm.started.value || vm.starting.value) return
        chooserOpen = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else launchVideoPicker()
    }
    LaunchedEffect(route) { vm.open(route.sessionId) }
    LaunchedEffect(timerVm.nextRoute) {
        timerVm.nextRoute?.let { showLeaveConfirm = false; vm.pause(); navigator.replace(it); timerVm.consumeRoute() }
    }
    LaunchedEffect(timerVm.discardComplete) {
        if (timerVm.discardComplete) {
            showLeaveConfirm = false
            vm.pause()
            navigator.pop()
            timerVm.consumeDiscard()
        }
    }
    LaunchedEffect(showLeaveConfirm, starting, liveSession.status) {
        if (showLeaveConfirm && !vm.starting.value && vm.session.value.status == TimerStatus.IDLE &&
            !timerVm.discarding && !timerVm.busy) showLeaveConfirm = false
    }
    var capsuleExpanded by remember { mutableStateOf(false) }
    val panelVisibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    panelVisibility.targetState = capsuleExpanded
    val panelNeedsBackdrop = panelVisibility.currentState || panelVisibility.targetState || !panelVisibility.isIdle
    var capsulePosition by remember { mutableStateOf(FloatingTimerPosition()) }
    val actions: @Composable (Backdrop, Boolean) -> Unit = { backdrop, fullscreen ->
        if (!started) {
            VideoGlassButton(
                onClick = ::choose, backdrop = backdrop, fullScreen = fullscreen,
                icon = AppIcons.Video, large = true, accent = true, enabled = canChooseVideo,
                label = stringResource(if (video == null) R.string.video_select else R.string.video_change),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            com.risediary.app.ui.timer.TimerActionDock(
                session, backdrop, onStart = {},
                onPause = { timerVm.pause(); vm.pause() },
                onResume = timerVm::resume,
                onFinish = { timerVm.requestFinish(vm::pause) },
                onRetry = timerVm::openRecord,
                transferring = timerVm.finishing, transferFailed = timerVm.transferFailed,
                enabled = !timerVm.busy && !timerVm.discarding && (!persistenceError || timerVm.transferFailed) &&
                    (!fullscreen || capsuleExpanded), fullScreen = fullscreen, compact = fullscreen, iconOnly = fullscreen
            )
        }
    }
    val extra: @Composable (Boolean, Backdrop) -> Unit = { _, backdrop ->
        Column(Modifier.fillMaxWidth()) {
            AnimatedUiVisibility(visible = started) { active ->
                VideoTimerSummary(session, false, notice, timerVm.error ?: commandError,
                    persistenceError, backdrop, timerVm::retryPersistence, enabled = active, sessionFlow = vm.session)
            }
            AnimatedUiVisibility(visible = !started && video != null) {
                Text(stringResource(R.string.video_start_hint), fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
    val overlay: (@Composable (Backdrop) -> Unit)? = if (!started) null else { backdrop ->
        FloatingTimerCapsule(session, backdrop, capsulePosition, { capsulePosition = it },
            capsuleExpanded, { capsuleExpanded = it }, timerVm.error ?: commandError,
            notice, persistenceError, timerVm::retryPersistence, vm.session, panelVisibility) { actions(backdrop, true) }
    }
    VideoPlayerPage(route = route, controller = vm.controller, loading = loading,
        problem = problem ?: playbackError, title = stringResource(R.string.mode_select_video),
        onPause = vm::pause, onRetry = vm::retry,
        onBack = {
            if (!timerVm.finishing && !timerVm.discarding && !timerVm.busy) {
                vm.pause()
                val current = vm.session.value
                if (vm.starting.value || (current.sessionId == route.sessionId && current.isActive))
                    showLeaveConfirm = true
                else navigator.pop()
            }
        }, extraContent = extra,
        bottomAction = { backdrop -> actions(backdrop, false) },
        loadingIndicator = showVideoLoadingIndicator(loading, true, video != null, started, selectingVideo),
        fullscreenOverlay = overlay,
        fullscreenOverlayNeedsBackdrop = panelNeedsBackdrop,
        onChooseVideo = if (canChooseVideo) ::choose else null,
        collapseFullscreenOverlay = {
            val wasExpanded = capsuleExpanded
            capsuleExpanded = false
            wasExpanded
        },
        interceptBack = starting || showLeaveConfirm ||
            (liveSession.sessionId == route.sessionId && liveSession.isActive) ||
            timerVm.busy || timerVm.finishing || timerVm.discarding)
    com.risediary.app.ui.components.PageBackHandler(enabled = timerVm.finishing || timerVm.discarding) {}
    TimerFinishDialogs(session, persistenceError, timerVm)
    if (showLeaveConfirm) {
        com.risediary.app.ui.timer.TimerLeaveDialog(liveSession, route.sessionId, timerVm,
            onLeave = { showLeaveConfirm = false; vm.pause(); navigator.pop() },
            onDismiss = { showLeaveConfirm = false },
            beforeDiscard = vm::pause, starting = starting)
    }
}
