/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.update

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.theme.backgroundBrush
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlurEffect

/** Only the page is captured. Popup content and the effect itself stay outside. */
@Composable
internal fun UpdateSheetBackdrop(
    state: UpdateSheetPresentation,
    interactionsBlocked: Boolean,
    content: @Composable () -> Unit,
) {
    val visibilityIntensity = remember { Animatable(0f) }
    var appliedOpeningId by remember { mutableLongStateOf(0L) }
    val openingId = state.openingId
    val openingStart = state.openingMultiplier
    LaunchedEffect(state.visible, interactionsBlocked, openingId) {
        if (appliedOpeningId != openingId) {
            // A reopen can interrupt a partially faded preview. Fold its actual strength in
            // even if the new opening was closed again before the next animation frame.
            visibilityIntensity.snapTo(visibilityIntensity.value * openingStart)
            appliedOpeningId = openingId
            state.onOpeningApplied(openingId)
        }
        if (state.visible && !interactionsBlocked) {
            visibilityIntensity.animateTo(1f, folmeSpring(damping = 0.9f, response = 0.38f))
        } else {
            visibilityIntensity.animateTo(0f, folmeSpring(damping = 0.9f, response = 0.38f))
        }
    }
    // Preserve the previous strength before the opening effect has had a chance to fold it in.
    val openingMultiplier = if (appliedOpeningId != openingId) state.openingMultiplier else 1f
    // Only visibility is animated here. Predictive progress and native settle geometry are direct.
    val intensity = visibilityIntensity.value.coerceIn(0f, 1f) * openingMultiplier * state.backdropMultiplier
    val drawing = !interactionsBlocked && (state.retained || visibilityIntensity.value > 0.001f)
    val background = backgroundBrush()
    val latestBackground by rememberUpdatedState(background)
    val supported = isRuntimeShaderSupported()
    val capture = if (drawing && supported) {
        rememberLayerBackdrop {
            drawRect(brush = latestBackground)
            drawContent()
        }
    } else null
    val colors = remember { BlurColors() }
    val radius = with(LocalDensity.current) { 16.dp.toPx() }
    Box(Modifier.fillMaxSize().background(background)) {
        val pageEffect = when {
            capture != null -> Modifier.layerBackdrop(capture)
            drawing && !supported -> Modifier.blur(16.dp * intensity.coerceIn(0f, 1f),
                edgeTreatment = BlurredEdgeTreatment.Rectangle)
            else -> Modifier
        }
        Box(Modifier.fillMaxSize().then(pageEffect)) { content() }
        if (drawing) {
            val effect = if (capture != null) Modifier.drawBackdrop(
                backdrop = capture,
                shape = { RectangleShape },
                effects = {
                    textureBlurEffect(
                        blurRadiusX = radius * intensity.coerceIn(0f, 1f),
                        noiseCoefficient = 0f,
                        colors = colors,
                    )
                },
            ) else Modifier
            Box(Modifier.fillMaxSize().then(effect).drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = 0.12f * intensity.coerceIn(0f, 1f)))
            })
        }
    }
}
