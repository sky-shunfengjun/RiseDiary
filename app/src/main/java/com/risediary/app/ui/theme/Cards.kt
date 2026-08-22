package com.risediary.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

enum class RiseCardStyle {
    Standard,
    Emphasis,
    Metric,
}

/**
 * Shared RiseDiary surface.
 *
 * The default remains source-compatible with existing call sites. New screens can
 * opt into emphasis or metric surfaces and an animated click action.
 */
@Composable
fun RiseCard(
    modifier: Modifier = Modifier,
    style: RiseCardStyle = RiseCardStyle.Standard,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalRiseDarkTheme.current
    val containerColor = when (style) {
        RiseCardStyle.Standard ->
            if (isDark) Color(0xFF20242B) else Color(0xF7FFFFFF)
        RiseCardStyle.Emphasis ->
            if (isDark) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
        RiseCardStyle.Metric ->
            if (isDark) Color.White.copy(alpha = 0.055f)
            else Color(0xFFF4F7FB)
    }
    val border = if (isDark) {
        BorderStroke(0.5.dp, Color.White.copy(alpha = 0.07f))
    } else {
        null
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "riseCardScale"
    )
    val cornerRadius = 24.dp
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
    Card(
        modifier = cardModifier,
        cornerRadius = cornerRadius,
        colors = CardDefaults.defaultColors(color = containerColor),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (enabled) onClick else null,
        content = content
    )
}
