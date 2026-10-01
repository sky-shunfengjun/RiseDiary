/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.risediary.app.ui.theme.backgroundBrush
import kotlin.math.pow
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Also disables offscreen pager pages and the content under the lock overlay. */
val LocalPageEffectsActive = staticCompositionLocalOf { true }

@Composable
fun rememberTopBlurProgress(state: ScrollState): () -> Float {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(state, threshold) { { (state.value / threshold).coerceIn(0f, 1f) } }
}

@Composable
fun rememberTopBlurProgress(state: LazyListState): () -> Float {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(state, threshold) {
        // Remember leading item extents so short headers still use the full 48dp
        // ramp when the second item becomes first-visible. Bound work and storage.
        val leadingSizes = mutableMapOf<Int, Int>()
        val readProgress: () -> Float = {
            val index = state.firstVisibleItemIndex
            if (!state.canScrollBackward) 0f else if (index > 16) 1f else {
                val info = state.layoutInfo
                info.visibleItemsInfo.filter { it.index < 16 }.forEach { leadingSizes[it.index] = it.size }
                val before = (0 until index).sumOf { leadingSizes[it] ?: threshold.toInt() }
                ((before + index * info.mainAxisItemSpacing + state.firstVisibleItemScrollOffset) / threshold)
                    .coerceIn(0f, 1f)
            }
        }
        readProgress
    }
}

@Composable
fun rememberTopBlurProgress(state: LazyGridState): () -> Float {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(state, threshold) {
        {
            when {
                !state.canScrollBackward -> 0f
                state.firstVisibleItemIndex > 0 -> 1f
                else -> (state.firstVisibleItemScrollOffset / threshold).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Captures an opaque page background plus content, independently of Kyant's
 * glass backdrop. The effect is draw-only; fixed controls render in [overlay].
 * Parameters follow HyperIsland's BlurBars.kt (miuix progressiveTextureBlur).
 */
@Composable
fun PageTopBlurLayout(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    topBarHeight: Dp = 48.dp,
    fadeHeight: Dp = 0.dp,
    includeStatusBar: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = LocalPageEffectsActive.current && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val latestProgress by rememberUpdatedState(progress)
    val intensity by remember(active) {
        derivedStateOf { if (active) latestProgress().coerceIn(0f, 1f) else 0f }
    }
    val background = backgroundBrush()
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = if (active && intensity > 0f && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(brush = background)
            drawContent()
        }
    } else null
    val statusHeight = if (includeStatusBar) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else 0.dp

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            content = content,
        )
        if (intensity > 0f) {
            val effect = if (backdrop != null) {
                Modifier.progressiveTextureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 16f * intensity,
                    gradient = TopBarProgressiveBlur,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(color = surface.copy(alpha = 0.66f))),
                    ),
                )
            } else {
                // Android 12 never creates a RuntimeShader or a miuix capture layer.
                Modifier.drawWithCache {
                    val stops = Array(17) { index ->
                        val fraction = index / 16f
                        val fade = ((fraction - 0.12f) / 0.88f).coerceIn(0f, 1f)
                        fraction to surface.copy(alpha = 0.66f * (1f - fade).pow(1.25f))
                    }
                    val brush = Brush.verticalGradient(*stops, endY = size.height)
                    onDrawBehind { drawRect(brush) }
                }
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(statusHeight + topBarHeight + fadeHeight)
                    .graphicsLayer { alpha = intensity }
                    .then(effect),
            )
        }
        overlay()
    }
}

private val TopBarProgressiveBlur = ProgressiveBlur.Top.copy(
    startFraction = 0.12f,
    endFraction = 1f,
    curve = 1.25f,
)
