package com.risediary.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

enum class RiseCardStyle {
    Standard,
    Emphasis,
    Metric,
}

internal fun riseCardPressFeedbackEnabled(
    hasOnClick: Boolean,
    enabled: Boolean
): Boolean = hasOnClick && enabled

/**
 * Shared RiseDiary surface.
 *
 * When [onClick] is provided the Miuix indication is drawn inside the card
 * surface, covering the complete rounded surface instead of adding an
 * external drop shadow.
 */
@Composable
fun RiseCard(
    modifier: Modifier = Modifier,
    style: RiseCardStyle = RiseCardStyle.Standard,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    allowContentOverflow: Boolean = false,
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
    val cornerRadius = 24.dp
    val shape = RoundedCornerShape(cornerRadius)
    val border = if (isDark) {
        BorderStroke(0.5.dp, Color.White.copy(alpha = 0.07f))
    } else {
        null
    }

    val isClickable = onClick != null || onLongClick != null
    val pressFeedbackEnabled = riseCardPressFeedbackEnabled(isClickable, enabled)

    if (allowContentOverflow && onClick == null && onLongClick == null) {
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer
        ) {
            Column(
                modifier = modifier
                    .background(containerColor, shape)
                    .then(if (border != null) Modifier.border(border, shape) else Modifier)
            ) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier
                .then(if (border != null) Modifier.border(border, shape) else Modifier),
            cornerRadius = cornerRadius,
            colors = CardDefaults.defaultColors(color = containerColor),
            pressFeedbackType = PressFeedbackType.None,
            showIndication = pressFeedbackEnabled,
            onClick = onClick?.takeIf { pressFeedbackEnabled },
            onLongPress = onLongClick?.takeIf { pressFeedbackEnabled },
            content = content
        )
    }
}
