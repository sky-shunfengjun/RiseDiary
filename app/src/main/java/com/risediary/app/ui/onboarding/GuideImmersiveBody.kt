/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.components.PageTopBlurLayout
import com.risediary.app.ui.onboarding.original.originalOnboardingBackground

/** Insets belong to the scrolling content; the viewport continues behind both pieces of chrome. */
internal val LocalGuideContentPadding = staticCompositionLocalOf { PaddingValues(0.dp) }

/** Uses the existing app blur, outside the glass sampling source and below fixed navigation. */
@Composable
internal fun GuideImmersiveBody(
    padding: PaddingValues,
    topBlurProgress: () -> Float,
    content: @Composable () -> Unit,
) {
    val color = originalOnboardingBackground()
    PageTopBlurLayout(
        progress = topBlurProgress,
        topBarHeight = 64.dp,
        fadeHeight = 24.dp,
        background = Brush.verticalGradient(listOf(color, color)),
        modifier = Modifier.testTag("update_intro_immersive_scene"),
        overlay = {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(padding.calculateBottomPadding() + 16.dp)
                .background(Brush.verticalGradient(
                    0f to color.copy(alpha = 0f),
                    0.55f to color.copy(alpha = 0.90f),
                    1f to color,
                )).testTag("update_intro_bottom_fade"))
        },
    ) {
        CompositionLocalProvider(LocalGuideContentPadding provides padding) { content() }
    }
}
