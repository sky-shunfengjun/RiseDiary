package com.risediary.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Existing glass recipe, with measured text so accessibility sizes do not clip. */
@Composable
internal fun LiquidActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    fullScreen: Boolean = false,
    surfaceColor: Color = Color.Unspecified
) {
    val colors = MiuixTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    BoxWithConstraints(modifier) {
        val inset = with(density) { 60.dp.roundToPx() }
        val labelWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - inset).coerceAtLeast(1) else Constraints.Infinity
        val layout = measurer.measure(text, style = style, maxLines = 2, constraints = Constraints(maxWidth = labelWidth))
        val desiredWidth = with(density) { (layout.size.width + inset).toDp() }.coerceAtLeast(80.dp)
        val width = if (constraints.hasBoundedWidth) minOf(desiredWidth, maxWidth) else desiredWidth
        val height = maxOf(uiActionHeightDp(density.fontScale).dp, with(density) { layout.size.height.toDp() } + 24.dp)
        val foreground = if (fullScreen) Color.White else if (destructive) colors.error else colors.onSurface
        LiquidGlassButton(
            onClick = onClick, backdrop = backdrop,
            modifier = Modifier.width(width).graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
                compositingStrategy = CompositingStrategy.ModulateAlpha
                clip = false
            },
            enabled = enabled, isInteractive = enabled,
            height = height, horizontalPadding = 16.dp,
            tint = (if (destructive) colors.error else colors.primary).copy(alpha = 0.06f),
            surfaceColor = if (fullScreen) Color.Black.copy(alpha = 0.22f) else surfaceColor,
            highlightIntensity = 0.38f, highlightRadiusMultiplier = 1f, pressExpansion = 2.dp
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = foreground)
            Text(text, Modifier.weight(1f), color = foreground, style = style, maxLines = 2)
        }
    }
}

/** One place for related messages; callers retain their original retry semantics. */
@Composable
internal fun InlineStatusContent(
    messages: List<String>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    onRetry: (() -> Unit)? = null,
    enabled: Boolean = true,
    fullScreen: Boolean = false,
    error: Boolean = true
) {
    val visibleMessages = messages.filter { it.isNotBlank() }.distinct()
    if (visibleMessages.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val ownBackdrop = rememberLayerBackdrop { drawRect(colors.surface); drawContent() }
    // A status region can live inside the page's capture layer: never sample its own glass.
    val sharedBackdrop = backdrop
    val foreground = if (fullScreen) Color.White else if (error) colors.error else colors.onSurfaceVariantSummary
    Box(modifier) {
        if (sharedBackdrop == null) Box(Modifier.matchParentSize().layerBackdrop(ownBackdrop))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (error) AppIcons.Info else AppIcons.Schedule, null, Modifier.size(18.dp), tint = foreground)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visibleMessages.forEach { Text(it, color = foreground, fontSize = 12.sp) }
                }
            }
            onRetry?.let {
                LiquidActionButton(stringResource(R.string.action_retry), AppIcons.Refresh, it,
                    sharedBackdrop ?: ownBackdrop, enabled = enabled, fullScreen = fullScreen)
            }
        }
    }
}

/** Size and alpha move independently; no offscreen buffer clips external glass shadows. */
@Composable
internal fun AnimatedUiVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (active: Boolean) -> Unit
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 170 else 120), label = "ui_content_alpha")
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            compositingStrategy = CompositingStrategy.ModulateAlpha
            clip = false
        }.then(if (!visible) Modifier.clearAndSetSemantics { } else Modifier),
        enter = expandVertically(tween(190), clip = false),
        exit = shrinkVertically(tween(150), clip = false)
    ) { content(visible) }
}
