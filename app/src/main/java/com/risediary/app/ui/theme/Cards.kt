package com.risediary.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
            if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
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
    val cardModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(
        defaultElevation = if (isDark || style == RiseCardStyle.Metric) 0.dp else 1.dp
    )
    val shape = RoundedCornerShape(24.dp)

    if (onClick == null) {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            content = content
        )
    } else {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content
        )
    }
}
