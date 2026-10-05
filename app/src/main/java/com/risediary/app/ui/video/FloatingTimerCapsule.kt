package com.risediary.app.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.timer.TimerInstrument
import kotlin.math.roundToInt

/** The transparent clock stays anchored while a glass surface expands around it. */
@Composable
internal fun FloatingTimerCapsule(
    session: TimerSession,
    backdrop: Backdrop,
    position: FloatingTimerPosition,
    onPositionChange: (FloatingTimerPosition) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    error: String?,
    notice: String?,
    persistenceError: Boolean,
    onRetryPersistence: () -> Unit,
    actions: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val expandedDescription = stringResource(if (expanded) R.string.video_timer_collapse else R.string.video_timer_expand)
    Box(Modifier.fillMaxSize()) {
        if (expanded) {
            Box(Modifier.matchParentSize().testTag("timer_panel_dismiss")
                .pointerInput(Unit) { detectTapGestures { onExpandedChange(false) } })
        }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val capsuleWidth = floatingTimerCapsuleWidth(maxWidth.value, density.fontScale).dp
            val clockWidth = (capsuleWidth.value - 16f).coerceAtLeast(1f)
            val digitHeight = minOf(22f * density.fontScale, clockWidth * 52f / 258f) * 60f / 52f
            val capsuleHeight = minOf(maxHeight, maxOf(48.dp, (19f + 18f * density.fontScale + digitHeight).dp))
            var capsuleSize by remember { mutableStateOf(IntSize.Zero) }
            var panelSize by remember { mutableStateOf(IntSize.Zero) }
            val measuredWidth = if (capsuleSize.width > 0) capsuleSize.width.toFloat() else with(density) { capsuleWidth.toPx() }
            val measuredHeight = if (capsuleSize.height > 0) capsuleSize.height.toFloat() else with(density) { capsuleHeight.toPx() }
            val anchor = floatingTimerOffset(position, widthPx, heightPx, measuredWidth, measuredHeight)
            val currentPosition by rememberUpdatedState(position)
            val currentMove by rememberUpdatedState(onPositionChange)
            val currentExpand by rememberUpdatedState(onExpandedChange)
            val currentGeometry by rememberUpdatedState(floatArrayOf(widthPx, heightPx, measuredWidth, measuredHeight))
            val panelWidth = minOf(maxOf(184.dp, capsuleWidth), maxWidth)
            val panelRoom = maxOf(anchor.y + measuredHeight, heightPx - anchor.y).coerceIn(0f, heightPx)
            val panelWidthPx = with(density) { panelWidth.toPx() }
            val panelOffset = floatingTimerPanelOffset(anchor, measuredHeight, panelWidthPx,
                if (panelSize.height > 0) panelSize.height.toFloat()
                else (measuredHeight + with(density) { 90.dp.toPx() }).coerceAtMost(panelRoom),
                widthPx, heightPx)
            val above = panelOffset.y < anchor.y - 0.5f
            val origin = TransformOrigin(
                if (panelWidthPx > 0f) ((anchor.x + measuredWidth / 2f - panelOffset.x) / panelWidthPx).coerceIn(0f, 1f) else 0.5f,
                if (above) 1f else 0f
            )
            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier.offset { IntOffset(panelOffset.x.roundToInt(), panelOffset.y.roundToInt()) },
                enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 500f),
                    initialScale = 0.88f, transformOrigin = origin),
                exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.96f, transformOrigin = origin)
            ) {
                Column(Modifier.width(panelWidth).heightIn(max = with(density) { panelRoom.toDp() })
                    .animateContentSize(spring(dampingRatio = 1f, stiffness = 500f))
                    .onSizeChanged { panelSize = it }
                    .drawBackdrop(
                        backdrop = backdrop, shape = { RoundedCornerShape(28.dp) },
                        // Standard demo glass container; no new effect recipe.
                        effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.34f)); drawRect(Color.White.copy(alpha = 0.06f)) }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState())
                    .testTag("floating_timer_actions")) {
                    if (!above) Spacer(Modifier.height(capsuleHeight))
                    Column(Modifier.padding(horizontal = 14.dp).padding(top = if (above) 14.dp else 0.dp,
                        bottom = if (above) 0.dp else 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!above) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                        VideoTimerProblems(notice, error, persistenceError, true, backdrop,
                            onRetryPersistence, enabled = expanded)
                        actions()
                        if (above) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                    }
                    if (above) Spacer(Modifier.height(capsuleHeight))
                }
            }
            // Stable hit area and a single clock composition across expansion/dragging.
            Box(
                Modifier.offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) }
                    .width(capsuleWidth).height(capsuleHeight).onSizeChanged { capsuleSize = it }
                    .testTag("floating_timer_capsule")
                    .semantics(mergeDescendants = true) { stateDescription = expandedDescription }
                    .pointerInput(Unit) {
                        var dragPosition = currentPosition
                        detectDragGestures(
                            onDragStart = { dragPosition = currentPosition; currentExpand(false) },
                            onDrag = { change, delta ->
                                change.consume()
                                val geometry = currentGeometry
                                dragPosition = moveFloatingTimer(dragPosition, delta.x, delta.y,
                                    geometry[0], geometry[1], geometry[2], geometry[3])
                                currentMove(dragPosition)
                            }
                        )
                    }
                    .clickable(interactionSource = null, indication = null, role = Role.Button,
                        onClickLabel = expandedDescription) { onExpandedChange(!expanded) },
                contentAlignment = Alignment.Center
            ) {
                TimerInstrument(session, Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    compact = true, fullScreen = true)
            }
        }
    }
}
