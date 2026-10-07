@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import com.risediary.app.ui.projectState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.media.VideoPlayerController
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LocalVideoPlayer(
    controller: VideoPlayerController,
    surfaceOwner: VideoSurfaceOwner,
    fullScreen: Boolean,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleOrientation: () -> Unit = {},
    direction: VideoOrientation = VideoOrientation.LANDSCAPE,
    fullscreenOverlay: (@Composable (Backdrop) -> Unit)? = null,
    onBlankTap: () -> Boolean = { false },
    titleContent: (@Composable () -> Unit)? = null,
    concealed: Boolean = false,
    controlsEnabled: Boolean = true,
    surfaceEnabled: Boolean = true,
    coverContent: (@Composable (Backdrop) -> Unit)? = null,
    compactConcealed: Boolean = false,
    fullscreenOverlayNeedsBackdrop: Boolean = false
) {
    val actionable = controlsEnabled && !concealed && coverContent == null
    val videoFlow = remember(controller) { controller.playback.projectState { it?.video } }
    val video by videoFlow.collectAsStateWithLifecycle()
    val isPlaying by controller.isPlaying.collectAsStateWithLifecycle()
    val player = controller.player
    var duration by remember(player) { mutableLongStateOf(player.duration) }
    var wantsPlay by remember(player) { mutableStateOf(player.playWhenReady) }
    var ended by remember(player) { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var controlsVisible by rememberSaveable(fullScreen, video?.uriString) { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var controlsTouched by remember(fullScreen) { mutableStateOf(false) }
    val keepControls: () -> Unit = { controlsVisible = true; interaction++ }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration
                wantsPlay = player.playWhenReady
                ended = player.playbackState == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(fullScreen, isPlaying) {
        if (fullScreen && !isPlaying) controlsVisible = true
    }
    LaunchedEffect(fullScreen, isPlaying, controlsVisible, interaction, controlsTouched) {
        if (fullScreen && isPlaying && controlsVisible && !controlsTouched) {
            delay(5_000L)
            controlsVisible = false
        }
    }

    val surfaceBackdrop = rememberLayerBackdrop { drawRect(Color.Black); drawContent() }
    val controlsColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.035f)
    val currentControlsColor by rememberUpdatedState(controlsColor)
    val controlsSurface = if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    val currentControlsSurface by rememberUpdatedState(controlsSurface)
    val controlsBackdrop = rememberLayerBackdrop { drawRect(currentControlsSurface); drawContent() }
    val controlsTransition = updateTransition(controlsVisible, label = "video_controls")
    val controlsAlpha by controlsTransition.animateFloat(
        transitionSpec = { tween(if (targetState) 170 else 120) }, label = "video_controls_alpha"
    ) { if (it) 1f else 0f }
    val retainControls = controlsTransition.currentState || controlsTransition.targetState || controlsTransition.isRunning
    val controlsFade = Modifier.graphicsLayer {
        alpha = controlsAlpha
        compositingStrategy = CompositingStrategy.ModulateAlpha
        clip = false
    }.then(if (!controlsVisible) Modifier.clearAndSetSemantics { } else Modifier)
    val fullscreenScrim = remember {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.20f),
            0.28f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.78f)
        )
    }

    val currentSurfaceTap by rememberUpdatedState<(() -> Unit)?>(
        if (fullScreen) ({ if (!onBlankTap()) controlsVisible = !controlsVisible }) else null
    )
    val surface: @Composable () -> Unit = {
        if (surfaceEnabled && !concealed) {
            VideoSurface(surfaceOwner, Modifier.fillMaxSize(), currentSurfaceTap)
        }
    }
    if (fullScreen) {
        Box(modifier.background(Color.Black)) {
            Box(Modifier.fillMaxSize().then(if (retainControls || fullscreenOverlayNeedsBackdrop)
                Modifier.layerBackdrop(surfaceBackdrop) else Modifier)) {
                surface()
                // Only the readable backdrop fades; the native video surface is never animated.
                Box(Modifier.fillMaxSize().drawBehind {
                    drawRect(fullscreenScrim, alpha = controlsAlpha)
                })
            }
            if (retainControls) {
                Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp).then(controlsFade),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VideoGlassButton(
                        onClick = { if (controlsVisible && actionable) { keepControls(); onToggleOrientation() } },
                        backdrop = surfaceBackdrop, fullScreen = true, icon = AppIcons.Refresh,
                        description = stringResource(if (direction == VideoOrientation.LANDSCAPE)
                            R.string.video_switch_portrait else R.string.video_switch_landscape),
                        enabled = controlsVisible && actionable, dimWhenDisabled = false,
                        modifier = Modifier.size(48.dp)
                    )
                    VideoGlassButton(
                        onClick = { if (controlsVisible && actionable) onFullScreen() }, backdrop = surfaceBackdrop, fullScreen = true,
                        icon = AppIcons.ExitFullscreen,
                        description = stringResource(R.string.video_exit_fullscreen),
                        enabled = controlsVisible && actionable, dimWhenDisabled = false,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (retainControls) {
                    VideoTransportControls(controller, null, duration, wantsPlay, ended,
                        surfaceBackdrop, true, keepControls, modifier = controlsFade,
                        onTouch = { controlsTouched = it }, enabled = controlsVisible && actionable)
                }
            }
            fullscreenOverlay?.invoke(surfaceBackdrop)
        }
    } else {
        RiseCard(modifier, allowContentOverflow = true) {
            if (titleContent != null) titleContent() else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Video, contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(video?.displayName.orEmpty(), fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            // Only detail opts into a compact mask; the native picture never crossfades.
            val compactHeight = (156f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
            val coverShape = if (compactConcealed) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp) else RectangleShape
            AnimatedVideoBody(compactConcealed, Modifier.fillMaxWidth()) {
                if (concealed && compactConcealed) {
                    // Measure the mask itself so larger text never needs an inner scroll area.
                    Box(Modifier.fillMaxWidth().heightIn(min = compactHeight)
                        .background(MiuixTheme.colorScheme.surface, coverShape), contentAlignment = Alignment.Center) {
                        coverContent?.invoke(controlsBackdrop)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().then(if (!actionable) Modifier.clearAndSetSemantics { } else Modifier)) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(20.dp)).layerBackdrop(surfaceBackdrop).background(Color.Black)) {
                                surface()
                            }
                            VideoGlassButton(
                                onClick = { if (actionable) onFullScreen() }, backdrop = surfaceBackdrop, fullScreen = true,
                                icon = AppIcons.Fullscreen, description = stringResource(R.string.video_fullscreen),
                                enabled = actionable,
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(48.dp)
                            )
                        }
                        Box(Modifier.fillMaxWidth()) {
                            // Round only the captured tint leaf; keep sibling glass shadows uncut.
                            Box(Modifier.matchParentSize().layerBackdrop(controlsBackdrop)
                                .background(Brush.horizontalGradient(listOf(currentControlsColor, Color.Transparent)),
                                    RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)))
                            VideoTransportControls(controller, null, duration, wantsPlay, ended,
                                controlsBackdrop, false, keepControls,
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), enabled = actionable)
                        }
                    }
                    if (concealed || coverContent != null) {
                        Box(Modifier.matchParentSize().background(MiuixTheme.colorScheme.surface, coverShape),
                            contentAlignment = Alignment.Center) {
                            coverContent?.invoke(controlsBackdrop)
                        }
                    }
                }
            }
        }
    }
}

/** Animate measured height; clipping is released at rest so glass shadows stay intact. */
@Composable
private fun AnimatedVideoBody(animate: Boolean, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    if (!animate) { Box(modifier, content = content); return }
    val height = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var target by remember { mutableIntStateOf(0) }
    Layout(content = { Box(Modifier.fillMaxWidth(), content = content) },
        modifier = modifier.graphicsLayer { clip = height.value.roundToInt() != target }) { children, constraints ->
        val child = children.single().measure(constraints)
        if (target != child.height) {
            target = child.height
            val next = child.height.toFloat()
            scope.launch {
                if (height.value == 0f) height.snapTo(next)
                else height.animateTo(next, tween(190))
            }
        }
        val current = if (height.value == 0f) child.height else height.value.roundToInt()
        layout(child.width, constraints.constrainHeight(current)) { child.place(0, 0) }
    }
}

@Composable
private fun VideoSurface(
    surfaceOwner: VideoSurfaceOwner,
    modifier: Modifier,
    onTap: (() -> Unit)? = null
) {
    val currentTap by rememberUpdatedState(onTap)
    val tapDescription = stringResource(R.string.video_show_controls)
    AndroidView(
        factory = { context -> android.widget.FrameLayout(context).also { surfaceOwner.mount(it) } },
        modifier = modifier,
        update = { host ->
            surfaceOwner.mount(host).apply {
                setOnClickListener { currentTap?.invoke() }
                isClickable = onTap != null
                contentDescription = if (onTap != null) tapDescription else null
            }
        },
        onReset = surfaceOwner::unmount,
        onRelease = surfaceOwner::unmount
    )
}
