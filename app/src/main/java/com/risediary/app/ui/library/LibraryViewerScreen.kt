package com.risediary.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.components.PageBackHandler
import com.risediary.app.ui.components.*
import com.risediary.app.service.*
import com.risediary.app.ui.projectState
import com.risediary.app.ui.timer.*
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.video.*
import com.risediary.app.media.libraryMaterialId
import top.yukonga.miuix.kmp.basic.*

/** One controller and native surface; playback and the manually started timer are independent. */
@Composable
fun LibraryViewerScreen(route: Route.LibraryViewer, vm: LibraryViewerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val playbackError by vm.controller.error.collectAsStateWithLifecycle()
    val loadedFlow = remember(vm) { vm.controller.playback.projectState { it?.video?.libraryMaterialId() } }
    val loadedMaterial by loadedFlow.collectAsStateWithLifecycle()
    val timing by vm.timing.state.collectAsStateWithLifecycle()
    val controlsFlow = remember(vm) { vm.session.projectState { it.forControls() } }
    val liveSession by controlsFlow.collectAsStateWithLifecycle()
    // A browsing page must never observe another timer's terminal event and open its form.
    val timerVm: TimerViewModel? = if (timing.started) hiltViewModel() else null
    val session = timerVm?.discardDisplaySession ?: timerVm?.handoffSession ?: liveSession
    var showLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    val persistenceError by vm.persistenceError.collectAsStateWithLifecycle()
    val commandError by vm.commandError.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestStart(asset: com.risediary.app.data.library.LibraryAsset) {
        if (timing.lockedMaterialId != null) vm.timing.retry() else vm.start(asset)
        // Starting captures the click before a possible system permission dialog.
        if (vm.timing.state.value.starting && !permissionAsked && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionAsked = true
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Navigation entries may be STARTED during entry; only the Activity tells us whether
    // the app is actually in the background. Transition visibility is gated separately.
    val lifecycle = (LocalContext.current.findVideoActivity() as? LifecycleOwner)?.lifecycle
        ?: LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val active = LocalPageEffectsActive.current && navigator.current() == route && foreground
    val fullscreen = rememberVideoFullscreenState(vm.controller, active, initialDirection = VideoOrientation.PORTRAIT)
    val owner = remember(vm.controller) { VideoSurfaceOwner(vm.controller) }
    LaunchedEffect(route) { vm.open(route.assetId, route.openingId) }
    LaunchedEffect(active) {
        vm.setActive(active)
        if (active && !fullscreen.inSession) fullscreen.enter()
    }
    DisposableEffect(lifecycle, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                foreground = false; vm.onBackground()
            }
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.onBackground(); owner.release() }
    }
    fun leave() {
        if (timerVm?.busy == true || timerVm?.finishing == true || timerVm?.discarding == true) return
        vm.pause()
        if (timing.starting || (liveSession.sessionId == route.openingId && liveSession.isActive)) showLeaveConfirm = true
        else navigator.pop()
    }
    LaunchedEffect(timerVm?.nextRoute) {
        timerVm?.nextRoute?.let { showLeaveConfirm = false; vm.pause(); navigator.replace(it); timerVm.consumeRoute() }
    }
    LaunchedEffect(timerVm?.discardComplete) {
        if (timerVm?.discardComplete == true) { showLeaveConfirm = false; vm.pause(); navigator.pop(); timerVm.consumeDiscard() }
    }
    // A fresh page may first observe the limit result, without having observed its active clock.
    LaunchedEffect(timerVm, liveSession.sessionId, liveSession.status, liveSession.finishCandidate) {
        if (timerVm != null && liveSession.sessionId == route.openingId && liveSession.isTerminal &&
            liveSession.finishCandidate == null && !timerVm.discarding && !timerVm.discardComplete) {
            showLeaveConfirm = false
            timerVm.openRecord()
        }
    }
    LaunchedEffect(timing.starting, timing.started) {
        if (!timing.starting && !timing.started) showLeaveConfirm = false
    }
    PageBackHandler { leave() }
    val density = LocalDensity.current
    var controlsHeight by remember(density) { mutableStateOf(180.dp) }
    var headerHeight by remember(density) { mutableStateOf(60.dp) }
    var capsuleExpanded by rememberSaveable { mutableStateOf(false) }
    var capsuleX by rememberSaveable { mutableFloatStateOf(0f) }
    var capsuleY by rememberSaveable { mutableFloatStateOf(0f) }
    val panelVisibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    panelVisibility.targetState = capsuleExpanded
    val panelNeedsBackdrop = panelVisibility.currentState || panelVisibility.targetState || !panelVisibility.isIdle
    val enabled = active && !timing.starting && timerVm?.busy != true && timerVm?.finishing != true && timerVm?.discarding != true
    fun finish() {
        capsuleExpanded = false
        timerVm?.requestLibraryFinish(vm::pause)
    }
    val floating: @Composable (com.kyant.backdrop.Backdrop) -> Unit = { backdrop ->
        if (timing.started && timerVm != null) {
            FloatingTimerCapsule(session, backdrop, FloatingTimerPosition(capsuleX, capsuleY),
                { capsuleX = it.x; capsuleY = it.y }, capsuleExpanded, { capsuleExpanded = it },
                timerVm.error ?: commandError, null, persistenceError, timerVm::retryPersistence,
                vm.session, panelVisibility, topInset = headerHeight) {
                TimerActionDock(session, backdrop, {}, { timerVm.pause(); vm.pause() }, timerVm::resume,
                    ::finish, timerVm::openRecord, transferring = timerVm.finishing, transferFailed = timerVm.transferFailed,
                    enabled = enabled && capsuleExpanded && (!persistenceError || timerVm.transferFailed),
                    fullScreen = true, compact = true, iconOnly = true, finishWhileRunning = true)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.assets.isNotEmpty()) {
            val pager = rememberPagerState(initialPage = state.initialIndex, pageCount = { state.assets.size })
            val lockedIndex = state.assets.indexOfFirst { it.id == timing.lockedMaterialId }
            val currentIndex = if (lockedIndex >= 0) lockedIndex else pager.settledPage.coerceIn(state.assets.indices)
            val asset = state.assets[currentIndex]
            LaunchedEffect(lockedIndex) { if (lockedIndex >= 0 && pager.currentPage != lockedIndex) pager.scrollToPage(lockedIndex) }
            LaunchedEffect(asset.id) { vm.select(asset, foreground) }
            LaunchedEffect(pager.settledPage, state.assets.size) {
                if (pager.settledPage >= state.assets.size - 5) vm.more()
            }
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { state.assets.getOrNull(it)?.id ?: "unavailable:$it" }, beyondViewportPageCount = 0,
                userScrollEnabled = timing.ready && timing.lockedMaterialId == null) { index ->
                val item = state.assets.getOrNull(index)
                if (item != null && index == currentIndex) {
                    LocalVideoPlayer(vm.controller, owner, true, ::leave, Modifier.fillMaxSize(),
                        onToggleOrientation = fullscreen.toggleOrientation, direction = fullscreen.direction,
                        controlsEnabled = active && !timing.starting, surfaceEnabled = active,
                        showFullscreenExit = false, onFullscreenControlsHeight = { controlsHeight = it },
                        onBlankTap = { val expanded = capsuleExpanded; capsuleExpanded = false; expanded },
                        fullscreenOverlayNeedsBackdrop = panelNeedsBackdrop, fullscreenOverlay = floating,
                        fullscreenHeader = { visible ->
                            Row(Modifier.fillMaxWidth().safeDrawingPadding()
                                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                                .padding(start = 12.dp, end = 80.dp, top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = ::leave, enabled = visible && active && timerVm?.busy != true,
                                    modifier = Modifier.size(48.dp)) {
                                    Icon(AppIcons.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
                                }
                                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp))
                            }
                        },
                        fullscreenTransportAction = { backdrop, visible, size ->
                            LibraryTimerControls(if (timing.started) session else null, timing.starting, backdrop,
                                onStart = { requestStart(asset) }, onFinish = ::finish, onRetry = { timerVm?.openRecord() },
                                enabled = visible && enabled && timing.ready && (timing.started || loadedMaterial == asset.id) &&
                                    (!timing.started || !persistenceError || timerVm?.transferFailed == true),
                                transferring = timerVm?.finishing == true, transferFailed = timerVm?.transferFailed == true, buttonSize = size)
                        })
                } else if (item != null) LibraryCover(item, Modifier.fillMaxSize())
            }
        } else if (state.error == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        val error = playbackError ?: state.error ?: timing.error.takeIf { !timing.started }
        if (error != null) Box(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 64.dp,
            bottom = if (state.assets.isNotEmpty()) controlsHeight + 12.dp else 24.dp)) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth().verticalScroll(rememberScrollState())
                .background(Color.Black.copy(alpha = 0.8f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Color.White)
                TextButton(stringResource(R.string.action_retry), vm::retry)
            }
        }
        if (state.assets.isEmpty()) IconButton(onClick = ::leave, enabled = timerVm?.busy != true, modifier = Modifier.align(Alignment.TopStart)
            .safeDrawingPadding().padding(top = 12.dp, start = 12.dp).size(48.dp)) {
            Icon(AppIcons.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
        }
    }
    if (timerVm != null) {
        TimerFinishDialogs(session, persistenceError, timerVm)
        if (showLeaveConfirm) TimerLeaveDialog(liveSession, route.openingId, timerVm,
            onLeave = { showLeaveConfirm = false; vm.pause(); navigator.pop() }, onDismiss = { showLeaveConfirm = false },
            beforeDiscard = vm::pause, starting = timing.starting)
    }
    timing.otherTimer?.let { current -> LiquidAlertDialog(onDismissRequest = vm.timing::dismissOtherTimer,
        title = { Text("已有计时，请先处理当前计时") },
        confirmAction = RiseDialogAction("返回当前计时", {
            vm.timing.dismissOtherTimer()
            current.sessionId?.let { id -> resolveTimerNotificationRoute(TimerNotificationEntry(id, "return"), vm.session.value, locked = false) }
                ?.let { navigator.push(it) }
        }, DialogActionTone.Normal),
        dismissAction = RiseDialogAction("取消", vm.timing::dismissOtherTimer, DialogActionTone.Normal)) }
}
