package com.risediary.app.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.components.LocalPageBackdrop
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.components.PageBackHandler
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text


@Composable
fun VideoPlayerScreen(route: Route, vm: VideoPlayerViewModel = hiltViewModel()) {
    val loading by vm.loading.collectAsStateWithLifecycle()
    val accessError by vm.error.collectAsStateWithLifecycle()
    val playbackError by vm.controller.error.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    LaunchedEffect(route) {
        when (route) {
            is Route.RecordVideo -> vm.openRecord(route.flightId)
            is Route.VideoPreview -> vm.open(route.video)
            else -> error("Unsupported video route")
        }
    }
    VideoPlayerPage(route, vm.controller, loading, accessError ?: playbackError, stringResource(R.string.video_title),
        vm::pause, vm::retry, { navigator.pop() })
}

@Composable
internal fun VideoPlayerPage(
    route: Route, controller: com.risediary.app.media.VideoPlayerController,
    loading: Boolean, problem: String?, title: String,
    onPause: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit,
    extraContent: @Composable (Boolean, Backdrop) -> Unit = { _, _ -> },
    bottomAction: (@Composable (Backdrop) -> Unit)? = null,
    interceptBack: Boolean = false,
    fullscreenOverlay: (@Composable (Backdrop) -> Unit)? = null,
    collapseFullscreenOverlay: () -> Boolean = { false },
    loadingIndicator: Boolean = loading
) {
    val navigator = LocalNavigator.current
    val activity = LocalContext.current.findVideoActivity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val configuration = LocalConfiguration.current
    val active = LocalPageEffectsActive.current && navigator.current() == route
    var fullscreenPhase by rememberSaveable { mutableStateOf(FullscreenEntryPhase.CLOSED) }

    val fullscreenSession = fullscreenPhase != FullscreenEntryPhase.CLOSED
    val windowDirection = when (configuration.orientation) {
        android.content.res.Configuration.ORIENTATION_LANDSCAPE -> VideoOrientation.LANDSCAPE
        android.content.res.Configuration.ORIENTATION_PORTRAIT -> VideoOrientation.PORTRAIT
        else -> null
    }
    var orientation by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver(
        save = { listOf(it.direction.ordinal, if (it.autoDecided) 1 else 0, if (it.manual) 1 else 0) },
        restore = { VideoOrientationSession(VideoOrientation.entries[it[0]], it[1] == 1, it[2] == 1) }
    )) { mutableStateOf(VideoOrientationSession(VideoOrientation.PORTRAIT)) }
    // Use the matching configuration in this frame, before the effect commits OPEN.
    val fullScreen = completeFullscreenEntry(fullscreenPhase, orientation.direction, windowDirection) == FullscreenEntryPhase.OPEN
    var originalOrientation by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var originalStatusVisible by rememberSaveable { mutableStateOf(true) }
    var originalNavVisible by rememberSaveable { mutableStateOf(true) }
    var originalBarsBehavior by rememberSaveable { mutableIntStateOf(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT) }
    val player = controller.player
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    val snapshot by controller.playback.collectAsStateWithLifecycle()
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) { videoSize = size }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    fun requestDirection(direction: VideoOrientation) {
        val requested = if (direction == VideoOrientation.LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        activity?.let { if (it.requestedOrientation != requested) it.requestedOrientation = requested }
    }
    fun enterFullscreen() {
        if (fullscreenPhase != FullscreenEntryPhase.CLOSED || !active) return
        val window = activity?.window
        val insets = window?.decorView?.let(ViewCompat::getRootWindowInsets)
        originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        originalStatusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        originalNavVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        originalBarsBehavior = window?.let { WindowCompat.getInsetsController(it, it.decorView).systemBarsBehavior }
            ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        val size = player.videoSize
        orientation = beginVideoOrientation(size.width, size.height, size.pixelWidthHeightRatio,
            if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                VideoOrientation.LANDSCAPE else VideoOrientation.PORTRAIT)
        // Request the target first; keep the normal layout until configuration catches up.
        requestDirection(orientation.direction)
        fullscreenPhase = beginFullscreenEntry(orientation.direction, windowDirection)
    }
    LaunchedEffect(fullscreenSession, videoSize) {
        if (fullscreenSession) orientation = resolveVideoOrientation(orientation,
            videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
    }
    LaunchedEffect(fullscreenPhase, windowDirection, orientation.direction, active) {
        if (fullscreenPhase == FullscreenEntryPhase.WAITING_FOR_ROTATION && active) {
            val ready = completeFullscreenEntry(fullscreenPhase, orientation.direction, windowDirection)
            if (ready == FullscreenEntryPhase.OPEN) fullscreenPhase = ready
            else {
                // Some multi-window/device policies ignore orientation. Do not leave entry stuck.
                kotlinx.coroutines.delay(900L)
                fullscreenPhase = completeFullscreenEntry(fullscreenPhase, orientation.direction,
                    windowDirection, timedOut = true)
            }
        }
    }
    LaunchedEffect(activity, fullscreenSession, active, orientation.direction) {
        if (fullscreenSession && active) requestDirection(orientation.direction)
    }
    LaunchedEffect(activity, fullScreen, active) {
        if (fullScreen && active) activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(active) { if (!active) onPause() }
    DisposableEffect(lifecycle, controller) {
        val observer = VideoPlaybackLifecycleObserver(controller)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); onPause() }
    }
    DisposableEffect(activity, fullscreenSession, active) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val ownsWindow = fullscreenSession && active
        onDispose {
            if (ownsWindow && activity?.isChangingConfigurations != true) {
                activity?.requestedOrientation = originalOrientation
                if (originalStatusVisible) bars?.show(WindowInsetsCompat.Type.statusBars())
                else bars?.hide(WindowInsetsCompat.Type.statusBars())
                if (originalNavVisible) bars?.show(WindowInsetsCompat.Type.navigationBars())
                else bars?.hide(WindowInsetsCompat.Type.navigationBars())
                bars?.systemBarsBehavior = originalBarsBehavior
            }
        }
    }
    fun exitFullscreen() { collapseFullscreenOverlay(); fullscreenPhase = FullscreenEntryPhase.CLOSED }
    PageBackHandler(enabled = fullscreenSession || interceptBack) {
        if (fullscreenSession) {
            if (!fullScreen || !collapseFullscreenOverlay()) exitFullscreen()
        } else onBack()
    }

    // Keep native media ownership outside layout branches; never move Compose LayoutNodes.
    val surfaceOwner = remember(controller) { VideoSurfaceOwner(controller) }
    DisposableEffect(surfaceOwner) { onDispose { surfaceOwner.release() } }
    val videoContent: @Composable () -> Unit = {
        LocalVideoPlayer(controller, surfaceOwner, fullScreen,
            { if (fullScreen) exitFullscreen() else enterFullscreen() },
            if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            onToggleOrientation = { orientation = toggleVideoOrientation(orientation) },
            direction = orientation.direction, fullscreenOverlay = fullscreenOverlay,
            onBlankTap = collapseFullscreenOverlay)
    }
    val fullBackdrop = rememberLayerBackdrop { drawRect(Color.Black); drawContent() }
    if (fullScreen) {
        if (loading || problem != null || snapshot == null) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.matchParentSize().layerBackdrop(fullBackdrop)
                    .background(Brush.verticalGradient(listOf(Color(0xFF17202B), Color.Black))))
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    VideoStateContent(loadingIndicator, problem, true, bottomAction != null,
                        fullBackdrop, onRetry, ::exitFullscreen)
                }
                fullscreenOverlay?.invoke(fullBackdrop)
            }
        } else videoContent()
    } else {
        SecondaryPageScaffold(title = title, onBack = onBack, bottomAction = bottomAction) { padding ->
            val backdrop = requireNotNull(LocalPageBackdrop.current)
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val videoOrState: @Composable () -> Unit = {
                    if (loading || problem != null || snapshot == null) {
                        RiseCard(Modifier.fillMaxWidth(), allowContentOverflow = true) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 250.dp).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                VideoStateContent(loadingIndicator, problem, false, bottomAction != null, backdrop, onRetry)
                            }
                        }
                    } else videoContent()
                }
                if (maxWidth >= 640.dp && maxWidth > maxHeight && bottomAction != null) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { videoOrState() }
                        Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { extraContent(false, backdrop) }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        videoOrState()
                        extraContent(false, backdrop)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoStateContent(
    loading: Boolean,
    problem: String?,
    fullScreen: Boolean,
    timerMode: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit,
    onExitFullScreen: (() -> Unit)? = null
) {
    val colors = MiuixTheme.colorScheme
    val foreground = if (fullScreen) Color.White else colors.onSurface
    val secondary = if (fullScreen) Color.White.copy(alpha = 0.7f) else colors.onSurfaceVariantSummary
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (loading) {
            CircularProgressIndicator()
            Text(stringResource(R.string.video_loading), fontSize = 14.sp, color = secondary)
        } else {
            Box(Modifier.size(76.dp).background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center) {
                Icon(if (problem == null) AppIcons.Video else AppIcons.Info,
                    contentDescription = null, tint = if (fullScreen) Color.White else colors.primary,
                    modifier = Modifier.size(32.dp))
            }
            Text(problem ?: stringResource(R.string.video_empty_title), color = foreground,
                fontSize = if (problem == null) 17.sp else 14.sp,
                fontWeight = if (problem == null) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center)
            if (problem != null) {
                VideoGlassButton(onRetry, backdrop, icon = AppIcons.Refresh,
                    label = stringResource(R.string.action_retry), accent = true, fullScreen = fullScreen,
                    modifier = Modifier.width(132.dp))
            } else if (timerMode) {
                Text(stringResource(R.string.video_start_hint), color = secondary, fontSize = 12.sp)
            }
        }
        onExitFullScreen?.let {
            VideoGlassButton(it, backdrop, icon = AppIcons.ExitFullscreen,
                label = stringResource(R.string.video_exit_fullscreen), fullScreen = true)
        }
    }
}


private tailrec fun Context.findVideoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findVideoActivity()
    else -> null
}
