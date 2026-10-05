package com.risediary.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.media.Media3VideoPlayerController
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.media.VideoPlayerController
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.LiquidSlider
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Reuses the app's demo-derived glass recipe, press motion and icon aliases. */
@Composable
internal fun VideoGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    description: String? = null,
    fullScreen: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    large: Boolean = false,
    selected: Boolean = false,
    dimWhenDisabled: Boolean = true
) {
    val colors = MiuixTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val foreground = when {
        selected -> colors.primary
        fullScreen -> Color.White
        else -> colors.onSurface
    }
    LiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier
            .graphicsLayer {
                // Fade individual draws without a button-sized offscreen buffer cutting the shadow.
                alpha = if (enabled || !dimWhenDisabled) 1f else 0.45f
                compositingStrategy = CompositingStrategy.ModulateAlpha
                clip = false
            }
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) {
                contentDescription = description
            } else Modifier),
        enabled = enabled,
        isInteractive = enabled,
        tint = if (selected) colors.primary.copy(alpha = 0.22f) else if (accent) colors.primary.copy(alpha = 0.10f)
            else if (fullScreen) Color.White.copy(alpha = 0.08f)
            else colors.primary.copy(alpha = 0.035f),
        surfaceColor = if (selected && fullScreen) Color.White.copy(alpha = 0.86f)
            else if (fullScreen) Color.Black.copy(alpha = 0.22f) else Color.Unspecified,
        height = if (label == null) { if (large) 56.dp else 48.dp }
            else if (large) maxOf(56.dp, (32f * fontScale + 24f).dp)
            else maxOf(48.dp, (24f * fontScale + 20f).dp),
        horizontalPadding = if (label == null) 0.dp else 8.dp,
        highlightIntensity = 0.38f,
        highlightRadiusMultiplier = 1f,
        pressExpansion = 2.dp
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = if (accent && !fullScreen) colors.primary else foreground,
                modifier = Modifier.size(if (large) 24.dp else 22.dp))
        }
        label?.let {
            Text(it, color = foreground, fontSize = if (large) 16.sp else 13.sp,
                fontWeight = if (large) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoTransportControls(
    controller: VideoPlayerController,
    snapshot: VideoPlaybackSnapshot?,
    durationMillis: Long,
    wantsPlay: Boolean,
    ended: Boolean,
    backdrop: Backdrop,
    fullScreen: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    onTouch: (Boolean) -> Unit = {},
    enabled: Boolean = true
) {
    var scrubFraction by remember(snapshot?.video?.uriString, durationMillis) { mutableStateOf<Float?>(null) }
    LaunchedEffect(enabled) {
        // Disabling cancels slider input without committing a seek; discard its held preview too.
        if (!enabled) scrubFraction = null
    }
    val progress = scrubFraction ?: videoProgressFraction(snapshot?.positionMillis ?: 0L, durationMillis)
    val position = scrubFraction?.let { videoSeekPosition(it, durationMillis) } ?: snapshot?.positionMillis ?: 0L
    val foreground = if (fullScreen) Color.White else MiuixTheme.colorScheme.onSurface
    val secondary = if (fullScreen) Color.White.copy(alpha = 0.72f) else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val canSeek = durationMillis > 0L
    val speed = snapshot?.speed ?: 1f
    val speedLabel = speed.toString().removeSuffix(".0")
    val speedDescription = stringResource(R.string.video_speed, speedLabel)
    val fontScale = LocalDensity.current.fontScale
    val loopDescription = stringResource(R.string.video_loop)
    val loopState = stringResource(if (snapshot?.loop == true) R.string.video_loop_on else R.string.video_loop_off)
    val progressDescription = stringResource(R.string.video_progress)

    fun interact(action: () -> Unit) {
        if (!enabled) return
        onInteraction()
        action()
    }

    val currentTouch by rememberUpdatedState(onTouch)
    Column(modifier.then(if (enabled) Modifier.pointerInput(Unit) {
        // Observe without consuming slider/button gestures; a held control must not fade away.
        awaitEachGesture {
            try {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                currentTouch(true)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
            } finally { currentTouch(false) }
        }
    } else Modifier), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatVideoPosition(position), fontSize = 12.sp, color = secondary)
            Text(formatVideoPosition(durationMillis.takeIf { it > 0L }), fontSize = 12.sp, color = secondary)
        }
        if (canSeek) {
            LiquidSlider(
                value = { progress },
                onValueChange = { if (enabled) { scrubFraction = it; onInteraction() } },
                valueRange = 0f..1f,
                steps = 0,
                backdrop = backdrop,
                modifier = Modifier.semantics { contentDescription = progressDescription },
                onValueChangeFinished = {
                    if (enabled) {
                        scrubFraction?.let { fraction ->
                            videoSeekPosition(fraction, durationMillis)?.let(controller::seekTo)
                        }
                        onInteraction()
                    }
                    scrubFraction = null
                },
                enabled = enabled
            )
        } else {
            // An unread duration must not expose a slider with an invalid range or seek action.
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(3.dp)
                    .backgroundForVideoProgress(secondary.copy(alpha = 0.18f)))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val viewportWidth = minOf(maxWidth, 560.dp)
            val buttonSize = if (viewportWidth >= 308.dp) 52.dp else 48.dp
            val playSize = buttonSize + 8.dp
            val speedWidth = maxOf(buttonSize, (16f + 36f * fontScale).dp)
            val requiredWidth = speedWidth + buttonSize * 3 + playSize + 32.dp
            val overflowing = requiredWidth > viewportWidth
            val scroll = rememberScrollState()
            val speedButton: @Composable () -> Unit = {
                VideoGlassButton(
                    onClick = { interact {
                        val choices = Media3VideoPlayerController.SPEEDS
                        controller.setSpeed(choices[(choices.indexOf(speed) + 1) % choices.size])
                    } },
                    backdrop = backdrop, fullScreen = fullScreen,
                    label = stringResource(R.string.video_speed_value, speedLabel),
                    enabled = enabled, dimWhenDisabled = enabled,
                    description = speedDescription, modifier = Modifier.width(speedWidth).height(maxOf(buttonSize, (24f * fontScale + 20f).dp))
                )
            }
            val backButton: @Composable () -> Unit = {
                VideoGlassButton(
                    onClick = { interact {
                        val current = (snapshot?.positionMillis ?: 0L).coerceIn(0L, durationMillis)
                        controller.seekTo(current - controller.player.seekBackIncrement.coerceIn(0L, current))
                    } },
                    backdrop = backdrop, fullScreen = fullScreen, icon = AppIcons.FastRewind,
                    description = stringResource(R.string.video_seek_back, (controller.player.seekBackIncrement / 1_000L).toInt()),
                    enabled = enabled && canSeek, dimWhenDisabled = enabled, modifier = Modifier.size(buttonSize)
                )
            }
            val playButton: @Composable () -> Unit = {
                VideoGlassButton(
                    onClick = { interact {
                        if (wantsPlay && !ended) controller.pause()
                        else {
                            if (ended) controller.seekTo(0L)
                            controller.play()
                        }
                    } },
                    backdrop = backdrop, fullScreen = fullScreen, accent = true, large = true,
                    icon = if (wantsPlay && !ended) AppIcons.Pause else AppIcons.PlayArrow,
                    enabled = enabled, dimWhenDisabled = enabled,
                    description = stringResource(if (wantsPlay && !ended) R.string.video_pause
                        else if (ended) R.string.video_replay else R.string.video_play),
                    modifier = Modifier.size(playSize)
                )
            }
            val forwardButton: @Composable () -> Unit = {
                VideoGlassButton(
                    onClick = { interact {
                        val current = (snapshot?.positionMillis ?: 0L).coerceIn(0L, durationMillis)
                        controller.seekTo(current + controller.player.seekForwardIncrement.coerceIn(0L, durationMillis - current))
                    } },
                    backdrop = backdrop, fullScreen = fullScreen, icon = AppIcons.FastForward,
                    description = stringResource(R.string.video_seek_forward, (controller.player.seekForwardIncrement / 1_000L).toInt()),
                    enabled = enabled && canSeek, dimWhenDisabled = enabled, modifier = Modifier.size(buttonSize)
                )
            }
            val loopButton: @Composable () -> Unit = {
                VideoGlassButton(
                    onClick = { interact { controller.setLoop(snapshot?.loop != true) } },
                    backdrop = backdrop, fullScreen = fullScreen,
                    accent = snapshot?.loop == true, selected = snapshot?.loop == true, description = loopDescription,
                    icon = AppIcons.EventRepeat,
                    enabled = enabled, dimWhenDisabled = enabled,
                    modifier = Modifier.size(buttonSize).semantics {
                        stateDescription = loopState
                        selected = snapshot?.loop == true
                    }
                )
            }
            // Fill the available row; only genuinely narrow/large-font layouts need scrolling.
            // Scroll clips along its axis. Reserve the default shadow's two-radius outset
            // at both ends only for overflow; fitting rows keep their full-width spacing.
            val edgePadding = if (overflowing) 48.dp else 4.dp
            val contentWidth = requiredWidth + (edgePadding - 4.dp) * 2
            val rowModifier = Modifier.width(viewportWidth)
                .then(if (overflowing) Modifier.horizontalScroll(scroll).widthIn(min = contentWidth) else Modifier)
                .padding(horizontal = edgePadding, vertical = 4.dp)
            Row(rowModifier,
                horizontalArrangement = if (overflowing) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                speedButton()
                backButton()
                playButton()
                forwardButton()
                loopButton()
            }
        }
    }
}

private fun Modifier.backgroundForVideoProgress(color: Color): Modifier =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
