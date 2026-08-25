/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only AND Apache-2.0
 *
 * This file is part of RiseDiary. RiseDiary's own code is licensed
 * under GPL-3.0-only (see LICENSE).
 *
 * Portions of this file are adapted and modified from AndroidLiquidGlass
 * by Kyant0 (https://github.com/Kyant0/AndroidLiquidGlass), which is
 * licensed under the Apache License, Version 2.0. Those portions remain
 * subject to Apache-2.0 (see LICENSES/Apache-2.0.txt).
 */
package com.risediary.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.ui.components.liquidglass.DampedDragAnimation
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import kotlin.math.round

/** AndroidLiquidGlass 1.0.0 Demo slider with RiseDiary's discrete-step support. */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !LocalRiseDarkTheme.current
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val trackColor = if (isLightTheme) {
        Color(0xFF787878).copy(0.2f)
    } else {
        Color(0xFF787880).copy(0.36f)
    }
    val useFullGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val intervalCount = steps + 1
    val interval =
        (valueRange.endInclusive - valueRange.start) / intervalCount.coerceAtLeast(1)
    fun snap(raw: Float): Float {
        if (steps <= 0) return raw.coerceIn(valueRange)
        if (interval <= 0f) return raw.coerceIn(valueRange)
        val position = (raw - valueRange.start) / interval
        return (valueRange.start + round(position) * interval).coerceIn(valueRange)
    }

    val trackBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value(), valueRange, steps)
                setProgress { requested ->
                    onValueChange(snap(requested))
                    true
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        val currentValue = value()
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var rawDragValue by remember { mutableFloatStateOf(currentValue) }
        var emittedValue by remember { mutableFloatStateOf(currentValue) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentValue,
                valueRange = valueRange,
                visibilityThreshold = interval.coerceAtLeast(0.001f),
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }
        val dragState = rememberDraggableState { delta ->
            if (trackWidth > 0) {
                val valueDelta =
                    (valueRange.endInclusive - valueRange.start) * (delta / trackWidth)
                rawDragValue =
                    (if (isLtr) rawDragValue + valueDelta else rawDragValue - valueDelta)
                        .coerceIn(valueRange)
                val snapped = snap(rawDragValue)
                if (snapped != emittedValue) {
                    emittedValue = snapped
                    onValueChange(snapped)
                }
                dampedDragAnimation.updateValue(rawDragValue)
            }
        }
        LaunchedEffect(currentValue) {
            if (!didDrag) {
                rawDragValue = currentValue
                emittedValue = currentValue
                if (dampedDragAnimation.targetValue != currentValue) {
                    dampedDragAnimation.updateValue(currentValue)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        didDrag = true
                        rawDragValue = currentValue
                        emittedValue = currentValue
                        dampedDragAnimation.press()
                    },
                    onDragStopped = {
                        val snapped = snap(rawDragValue)
                        if (snapped != emittedValue) {
                            emittedValue = snapped
                            onValueChange(snapped)
                        }
                        dampedDragAnimation.updateValue(snapped)
                        didDrag = false
                        dampedDragAnimation.release()
                    }
                )
                .pointerInput(animationScope) {
                    detectTapGestures { position ->
                        if (trackWidth <= 0) return@detectTapGestures
                        val delta = (valueRange.endInclusive - valueRange.start) *
                            (position.x / trackWidth)
                        val target = if (isLtr) {
                            valueRange.start + delta
                        } else {
                            valueRange.endInclusive - delta
                        }.coerceIn(valueRange)
                        val snapped = snap(target)
                        emittedValue = snapped
                        dampedDragAnimation.animateToValue(snapped)
                        onValueChange(snapped)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .layerBackdrop(trackBackdrop)
                    .fillMaxWidth()
                    .height(6.dp)
            ) {
                Box(
                    Modifier
                        .clip(ContinuousCapsule)
                        .background(trackColor)
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Box(
                    Modifier
                        .clip(ContinuousCapsule)
                        .background(accentColor)
                        .fillMaxWidth(dampedDragAnimation.progress.fastCoerceIn(0f, 1f))
                        .height(6.dp)
                )
            }
        }

        val thumbSurface = if (useFullGlass) {
            Modifier.drawBackdrop(
                backdrop = rememberCombinedBackdrop(
                    backdrop,
                    rememberBackdrop(trackBackdrop) { drawBackdrop ->
                        val progress = dampedDragAnimation.pressProgress
                        val scaleX = lerp(2f / 3f, 1f, progress)
                        val scaleY = lerp(0f, 1f, progress)
                        scale(scaleX, scaleY) { drawBackdrop() }
                    }
                ),
                shape = { ContinuousCapsule },
                effects = {
                    val progress = dampedDragAnimation.pressProgress
                    blur(8f.dp.toPx() * (1f - progress))
                    lens(
                        10f.dp.toPx() * progress,
                        14f.dp.toPx() * progress,
                        chromaticAberration = true
                    )
                },
                highlight = {
                    val progress = dampedDragAnimation.pressProgress
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = progress
                    )
                },
                shadow = {
                    Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                },
                innerShadow = {
                    val progress = dampedDragAnimation.pressProgress
                    InnerShadow(radius = 4.dp * progress, alpha = progress)
                },
                layerBlock = {
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                },
                onDrawSurface = {
                    val progress = dampedDragAnimation.pressProgress
                    drawRect(Color.White.copy(alpha = 1f - progress))
                }
            )
        } else {
            Modifier
                .shadow(4.dp, ContinuousCapsule)
                .background(Color.White, ContinuousCapsule)
                .graphicsLayer {
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(
                                -size.width / 4f,
                                trackWidth - size.width * 3f / 4f
                            ) * if (isLtr) 1f else -1f
                }
                .size(40.dp, 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.then(thumbSurface).size(40.dp, 24.dp))
        }
    }
}
