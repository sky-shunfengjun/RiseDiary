package com.risediary.app.ui.library

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

/** Clip the full, accessible label and soften only its overflowing end. No ellipsis glyph. */
@Composable
internal fun LibraryFadingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var overflow by remember(text, style) { mutableStateOf(false) }
    Text(text, modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val end = 40.dp.toPx().coerceAtMost(size.width / 2)
        val mask = Brush.horizontalGradient(
            0f to Color.White, ((size.width - end) / size.width.coerceAtLeast(1f)) to Color.White, 1f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            if (overflow && size.width > 0f && size.height > 0f) drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        onTextLayout = { overflow = it.didOverflowWidth })
}
