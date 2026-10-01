/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RectangleShape
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.theme.backgroundBrush
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Captures only this page's finished content; the effect never samples itself. */
@Composable
internal fun PageTransitionLayer(
    route: Route,
    motion: HyperIslandNavigationMotion,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = LocalNavTransitionScope.current
    DisposableEffect(route, motion, scope) {
        motion.register(route, scope)
        onDispose { motion.unregister(route, scope) }
    }
    val active = LocalPageEffectsActive.current
    val covered by remember(scope, active) {
        derivedStateOf { active && scope.relativeDepth > 0f && scope.relativeDepth < 1f }
    }
    val background = backgroundBrush()
    val latestBackground by rememberUpdatedState(background)
    val supported = isRuntimeShaderSupported()
    val backdrop = if (supported) {
        rememberLayerBackdrop {
            drawRect(brush = latestBackground)
            drawContent()
        }
    } else null
    val colors = remember { BlurColors() }
    val dimColor = MiuixTheme.colorScheme.windowDimming

    Box(Modifier.fillMaxSize().background(background)) {
        Box(
            Modifier.fillMaxSize().then(
                if (covered && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
            ),
        ) {
            content()
        }
        if (covered) {
            val blur = if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        textureBlurEffect(
                            blurRadiusX = 12f * motion.frame(scope).blurIntensity,
                            noiseCoefficient = 0f,
                            colors = colors,
                        )
                    },
                )
            } else Modifier
            Box(
                Modifier.fillMaxSize().then(blur).drawWithContent {
                    drawContent()
                    drawRect(dimColor.copy(alpha = 0.16f * motion.frame(scope).blurIntensity))
                },
            )
        }
        // Mask color follows miuix's theme, just like HyperIsland's backdrop.
        // Android 12 runs only this drawRect and geometry, without a blur capture.

        // Like HyperIsland's independent root bar: draw after the backdrop and
        // mask, outside the capture. The containing nav entry still stays below
        // secondary pages, so this never promotes the bar above their content.
        overlay()
    }
}
