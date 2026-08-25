package com.risediary.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.components.liquidglass.InteractiveHighlight
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import com.risediary.app.ui.icons.AppIcons

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
    val filledIcon: ImageVector
)

data class LiquidSegmentOption(val label: String, val icon: ImageVector)

@Composable
fun LiquidGlassBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onFlightClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        BottomTab(stringResource(R.string.bottom_tab_home), AppIcons.Home, AppIcons.HomeFilled),
        BottomTab(stringResource(R.string.bottom_tab_records), AppIcons.List, AppIcons.ListFilled),
        BottomTab(stringResource(R.string.bottom_tab_settings), AppIcons.Settings, AppIcons.SettingsFilled)
    )
    var visualSelectedTabIndex by rememberSaveable { mutableIntStateOf(selectedTabIndex) }
    var pendingNavigationIndex by remember { mutableStateOf<Int?>(null) }
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    LaunchedEffect(selectedTabIndex) { visualSelectedTabIndex = selectedTabIndex }
    LaunchedEffect(pendingNavigationIndex) {
        val index = pendingNavigationIndex ?: return@LaunchedEffect
        withFrameNanos {}
        currentOnTabSelected(index)
        if (pendingNavigationIndex == index) pendingNavigationIndex = null
    }
    val selectTab: (Int) -> Unit = { index ->
        if (index != visualSelectedTabIndex) {
            visualSelectedTabIndex = index
            pendingNavigationIndex = index
        }
    }
    val contentColor = if (!LocalRiseDarkTheme.current) Color.Black else Color.White
    val iconColorFilter = ColorFilter.tint(contentColor)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidBottomTabs(
            selectedTabIndex = { visualSelectedTabIndex },
            onTabSelected = selectTab,
            backdrop = backdrop,
            tabsCount = tabs.size,
            modifier = Modifier.weight(1f).selectableGroup()
        ) {
            tabs.forEachIndexed { index, tab ->
                LiquidBottomTab({ selectTab(index) }, Modifier.semantics { selected = index == visualSelectedTabIndex }) {
                    val sampling = LocalLiquidBottomTabSampling.current
                    Icon(
                        painter = rememberVectorPainter(if (sampling) tab.filledIcon else tab.icon),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp).graphicsLayer(colorFilter = iconColorFilter)
                    )
                    BasicText(tab.label, style = TextStyle(contentColor, 12.sp))
                }
            }
        }
        LiquidGlassButton(
            onClick = onFlightClick,
            backdrop = backdrop,
            modifier = Modifier.size(64.dp),
            highlightIntensity = 0.36f,
            highlightRadiusMultiplier = 0.98f,
            pressExpansion = 2.dp
        ) {
            Icon(AppIcons.FlightTakeoff, stringResource(R.string.bottom_tab_start_flight), tint = contentColor, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun LiquidSegmentedControl(
    options: List<LiquidSegmentOption>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 64.dp,
    contentPadding: Dp = 4.dp,
    showIcons: Boolean = true,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    showSelectionShadow: Boolean = true,
) {
    if (options.isEmpty()) return
    var visualSelectedIndex by rememberSaveable(options.size) { mutableIntStateOf(selectedIndex.fastCoerceIn(0, options.lastIndex)) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    val currentOnSelected by rememberUpdatedState(onSelected)
    val contentColor = if (LocalRiseDarkTheme.current) Color.White else Color.Black
    LaunchedEffect(selectedIndex, options.size) { visualSelectedIndex = selectedIndex.fastCoerceIn(0, options.lastIndex) }
    LaunchedEffect(pendingIndex) {
        val index = pendingIndex ?: return@LaunchedEffect
        withFrameNanos {}
        currentOnSelected(index)
        if (pendingIndex == index) pendingIndex = null
    }
    val select: (Int) -> Unit = { index ->
        if (index != visualSelectedIndex && index in options.indices) {
            visualSelectedIndex = index
            pendingIndex = index
        }
    }
    LiquidBottomTabs(
        { visualSelectedIndex }, select, backdrop, options.size, modifier.selectableGroup(),
        containerHeight, contentPadding, showSelectionShadow
    ) {
        options.forEachIndexed { index, option ->
            LiquidBottomTab({ select(index) }, Modifier.semantics { selected = index == visualSelectedIndex }) {
                if (showIcons) {
                    Icon(
                        painter = rememberVectorPainter(option.icon),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp).graphicsLayer(colorFilter = ColorFilter.tint(contentColor))
                    )
                }
                BasicText(
                    option.label,
                    style = TextStyle(
                        contentColor,
                        labelFontSize,
                        fontWeight = if (index == visualSelectedIndex) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
internal fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    onLongClick: (() -> Unit)? = null,
    highlightIntensity: Float = 1f,
    highlightRadiusMultiplier: Float = 1.5f,
    pressExpansion: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope, intensity = highlightIntensity, radiusMultiplier = highlightRadiusMultiplier)
    }
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
                layerBlock = if (isInteractive && enabled) {{
                    val width = size.width
                    val heightPx = size.height
                    if (width > 0f && heightPx > 0f) {
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + pressExpansion.toPx() / heightPx, progress)
                        val maxOffset = size.minDimension
                        val offset = interactiveHighlight.offset
                        if (maxOffset > 0f) {
                            translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                            translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                        }
                        val maxDragScale = pressExpansion.toPx() / heightPx
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / heightPx).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (heightPx / width).fastCoerceAtMost(1f)
                    }
                }} else null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = tint.alpha * 0.75f))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                }
            )
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = if (isInteractive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = if (isInteractive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            )
            .then(if (isInteractive && enabled) Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier) else Modifier)
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Compact two/three-option liquid-glass range switcher used inside card
 * headers. The outer box is sized to the compact control height while the
 * internal capture reserve stays tall enough for the press expansion, so
 * the glass pill never gets cut off at the control boundary.
 */
@Composable
fun CompactRangeSwitcher(
    options: List<LiquidSegmentOption>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    controlWidth: Dp = 124.dp,
    containerHeight: Dp = 38.dp,
) {
    val trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    val currentTrackColor by rememberUpdatedState(trackColor)
    val backdrop = rememberLayerBackdrop { drawContent() }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .requiredHeight(70.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(controlWidth)
                        .height(containerHeight)
                        .clip(ContinuousCapsule)
                        .background(currentTrackColor)
                )
            }
            LiquidSegmentedControl(
                options = options,
                selectedIndex = selectedIndex,
                onSelected = onSelected,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(controlWidth),
                containerHeight = containerHeight,
                contentPadding = 3.dp,
                showIcons = false,
                labelFontSize = 12.sp,
                showSelectionShadow = false
            )
        }
    }
}
