@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
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
    onBlankTap: () -> Boolean = { false }
) {
    val snapshot by controller.playback.collectAsStateWithLifecycle()
    val isPlaying by controller.isPlaying.collectAsStateWithLifecycle()
    val player = controller.player
    var duration by remember(player) { mutableLongStateOf(player.duration) }
    var wantsPlay by remember(player) { mutableStateOf(player.playWhenReady) }
    var ended by remember(player) { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var controlsVisible by rememberSaveable(fullScreen, snapshot?.video?.uriString) { mutableStateOf(true) }
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

    val currentSurfaceTap by rememberUpdatedState<(() -> Unit)?>(
        if (fullScreen) ({ if (!onBlankTap()) controlsVisible = !controlsVisible }) else null
    )
    val surface: @Composable () -> Unit = {
        VideoSurface(surfaceOwner, Modifier.fillMaxSize(), currentSurfaceTap)
    }
    if (fullScreen) {
        Box(modifier.background(Color.Black)) {
            Box(Modifier.fillMaxSize().layerBackdrop(surfaceBackdrop)) {
                surface()
                if (controlsVisible) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.28f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f)
                    )))
                }
            }
            AnimatedVisibility(
                visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VideoGlassButton(
                        onClick = { keepControls(); onToggleOrientation() },
                        backdrop = surfaceBackdrop, fullScreen = true, icon = AppIcons.Refresh,
                        description = stringResource(if (direction == VideoOrientation.LANDSCAPE)
                            R.string.video_switch_portrait else R.string.video_switch_landscape),
                        modifier = Modifier.size(48.dp)
                    )
                    VideoGlassButton(
                        onClick = onFullScreen, backdrop = surfaceBackdrop, fullScreen = true,
                        icon = AppIcons.ExitFullscreen,
                        description = stringResource(R.string.video_exit_fullscreen),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    VideoTransportControls(controller, snapshot, duration, wantsPlay, ended,
                        surfaceBackdrop, true, keepControls, onTouch = { controlsTouched = it })
                }
            }
            fullscreenOverlay?.invoke(surfaceBackdrop)
        }
    } else {
        RiseCard(modifier, allowContentOverflow = true) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Video, contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(snapshot?.video?.displayName.orEmpty(), fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp)).layerBackdrop(surfaceBackdrop).background(Color.Black)) {
                    surface()
                }
                VideoGlassButton(
                    onClick = onFullScreen, backdrop = surfaceBackdrop, fullScreen = true,
                    icon = AppIcons.Fullscreen, description = stringResource(R.string.video_fullscreen),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(48.dp)
                )
            }
            Box(Modifier.fillMaxWidth()) {
                // The captured gradient and the glass are siblings, so controls never sample themselves.
                Box(Modifier.matchParentSize().layerBackdrop(controlsBackdrop)
                    .background(Brush.horizontalGradient(listOf(currentControlsColor, Color.Transparent))))
                VideoTransportControls(controller, snapshot, duration, wantsPlay, ended,
                    controlsBackdrop, false, keepControls,
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp))
            }
        }
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
