package com.risediary.app.ui.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.components.AnimatedUiVisibility
import com.risediary.app.ui.components.LiquidActionButton
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
    loadingIndicator: Boolean = loading,
    onChooseVideo: (() -> Unit)? = null
) {
    val navigator = LocalNavigator.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalPageEffectsActive.current && navigator.current() == route
    val fullscreen = rememberVideoFullscreenState(controller, active)
    val fullScreen = fullscreen.fullScreen
    val fullscreenSession = fullscreen.inSession
    val snapshot by controller.playback.collectAsStateWithLifecycle()
    LaunchedEffect(active) { if (!active) onPause() }
    DisposableEffect(lifecycle, controller) {
        val observer = VideoPlaybackLifecycleObserver(controller)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); onPause() }
    }
    fun exitFullscreen() { collapseFullscreenOverlay(); fullscreen.exit() }
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
            { if (fullScreen) exitFullscreen() else fullscreen.enter() },
            if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            onToggleOrientation = fullscreen.toggleOrientation,
            direction = fullscreen.direction, fullscreenOverlay = fullscreenOverlay,
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
                        fullBackdrop, onRetry, onExitFullScreen = ::exitFullscreen)
                }
                fullscreenOverlay?.invoke(fullBackdrop)
            }
        } else videoContent()
    } else {
        SecondaryPageScaffold(title = title, onBack = onBack, bottomAction = bottomAction,
            adaptiveBottomActionSpace = true) { padding ->
            val backdrop = requireNotNull(LocalPageBackdrop.current)
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val videoOrState: @Composable () -> Unit = {
                    if (loading || problem != null || snapshot == null) {
                        VideoPlaceholderCard(loading, loadingIndicator, problem, bottomAction != null,
                            backdrop, onRetry, onChooseVideo = onChooseVideo)
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

/** The complete empty card is a selection target; loading/error previews retain their own actions. */
@Composable
internal fun VideoPlaceholderCard(
    loading: Boolean,
    loadingIndicator: Boolean,
    problem: String?,
    timerMode: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseVideo: (() -> Unit)? = null
) {
    val selectable = !loading && problem == null && onChooseVideo != null
    val description = stringResource(R.string.video_select)
    RiseCard(modifier.fillMaxWidth().then(if (selectable) Modifier.semantics(mergeDescendants = true) {
        contentDescription = description
    } else Modifier),
        onClick = onChooseVideo?.takeIf { selectable }, allowContentOverflow = true) {
        Column(Modifier.fillMaxWidth().heightIn(min = 250.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            VideoStateContent(loadingIndicator, problem, false, timerMode, backdrop, onRetry)
        }
    }
}

@Composable
internal fun VideoStateContent(
    loading: Boolean,
    problem: String?,
    fullScreen: Boolean,
    timerMode: Boolean,
    backdrop: Backdrop,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onExitFullScreen: (() -> Unit)? = null
) {
    val colors = MiuixTheme.colorScheme
    val foreground = if (fullScreen) Color.White else colors.onSurface
    val secondary = if (fullScreen) Color.White.copy(alpha = 0.7f) else colors.onSurfaceVariantSummary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Animate only Compose status content, never the AndroidView or player ownership branch.
        AnimatedContent(targetState = loading to problem, label = "video_state_content",
            transitionSpec = {
                (fadeIn(tween(170)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(190) }))
            }) { (stateLoading, stateProblem) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (stateLoading) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.video_loading), fontSize = 14.sp, color = secondary)
                } else {
                    Box(Modifier.size(76.dp).background(colors.primary.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(if (stateProblem == null) AppIcons.Video else AppIcons.Info,
                            contentDescription = null, tint = if (fullScreen) Color.White else colors.primary,
                            modifier = Modifier.size(32.dp))
                    }
                    Text(stateProblem ?: stringResource(R.string.video_empty_title), color = foreground,
                        fontSize = if (stateProblem == null) 17.sp else 14.sp,
                        fontWeight = if (stateProblem == null) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center)
                    if (stateProblem == null && timerMode) {
                        Text(stringResource(R.string.video_start_hint), color = secondary, fontSize = 12.sp)
                    }
                }
            }
        }
        AnimatedUiVisibility(visible = !loading && problem != null) { active ->
            if (fullScreen) {
                VideoGlassButton(onRetry, backdrop, icon = AppIcons.Refresh,
                    description = stringResource(R.string.action_retry), accent = true, fullScreen = true,
                    enabled = active, dimWhenDisabled = false, modifier = Modifier.size(52.dp))
            } else {
                LiquidActionButton(stringResource(R.string.action_retry), AppIcons.Refresh, onRetry,
                    backdrop, enabled = active)
            }
        }
        onExitFullScreen?.let {
            VideoGlassButton(it, backdrop, icon = AppIcons.ExitFullscreen,
                description = stringResource(R.string.video_exit_fullscreen), fullScreen = true,
                modifier = Modifier.size(52.dp))
        }
    }
}
