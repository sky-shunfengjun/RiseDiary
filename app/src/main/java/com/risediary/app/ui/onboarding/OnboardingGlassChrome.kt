/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.ui.components.LiquidBackButton
import com.risediary.app.ui.components.LiquidGlassButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val OnboardingBackShadowInsetDp = 12

/** Chrome samples the full viewport sibling, without painting a local background patch. */
@Composable
internal fun OnboardingGlassBack(enabled: Boolean, backdrop: Backdrop, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = if (enabled) 1f else 0.5f
        compositingStrategy = CompositingStrategy.ModulateAlpha
        clip = false
    }, contentAlignment = Alignment.Center) {
        LiquidBackButton(onClick, backdrop, Modifier.testTag("oobe_back"), enabled = enabled)
    }
}

@Composable
internal fun OnboardingGlassPrimary(text: String, enabled: Boolean, backdrop: Backdrop, onClick: () -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        val width = (constraints.maxWidth - with(density) { 40.dp.roundToPx() }).coerceAtLeast(1)
        val label = measurer.measure(text, style, constraints = Constraints(maxWidth = width))
        val height = maxOf(50.dp, with(density) { label.size.height.toDp() } + 28.dp)
        LiquidGlassButton(
            onClick, backdrop,
            modifier = Modifier.fillMaxWidth().testTag("oobe_footer").graphicsLayer {
                alpha = if (enabled) 1f else 0.5f
                compositingStrategy = CompositingStrategy.ModulateAlpha
                clip = false
            },
            enabled = enabled, isInteractive = enabled, height = height,
            tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.06f),
            highlightIntensity = 0.38f, highlightRadiusMultiplier = 1f, pressExpansion = 2.dp,
        ) { Text(text, style = style, color = MiuixTheme.colorScheme.onSurface) }
    }
}
