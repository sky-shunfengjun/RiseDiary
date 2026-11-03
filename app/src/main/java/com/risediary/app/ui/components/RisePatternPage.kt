/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class RiseTopAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

/** Nested previews inherit their host's consumed system bars explicitly. */
val LocalRisePageInsets = staticCompositionLocalOf<WindowInsets?> { null }

@Composable
fun RisePrimaryAction(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Save,
    enabled: Boolean = true
) = LiquidActionButton(text, icon, onClick, backdrop, modifier, enabled)

/** Order actions by priority (usually Add first); overflow never drops disabled actions. */
@Composable
fun RiseTopActions(actions: List<RiseTopAction>, availableSlots: Int, modifier: Modifier = Modifier) {
    val placement = topActionPlacement(actions.size, availableSlots)
    var expanded by remember { mutableStateOf(false) }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val menuHeight = (LocalWindowInfo.current.containerDpSize.height - 16.dp).coerceIn(48.dp, 360.dp)
    LaunchedEffect(placement.overflowIndices, actions.map { it.label }) { expanded = false }
    val positioner = remember(margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(0)
                val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(0)
                return IntOffset(
                    (if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left).coerceIn(margin.coerceAtMost(maxX), maxX),
                    anchorBounds.bottom.coerceIn(margin.coerceAtMost(maxY), maxY)
                )
            }
        }
    }
    val moreLabel = stringResource(R.string.action_more)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        placement.directIndices.forEach { index ->
            val action = actions[index]
            IconButton(action.onClick, Modifier.size(48.dp), enabled = action.enabled) {
                Icon(action.icon, action.label, Modifier.size(22.dp))
            }
        }
        if (placement.overflowIndices.isNotEmpty()) {
            Box {
                IconButton({ expanded = true }, Modifier.size(48.dp)) {
                    Icon(AppIcons.DragHandle, moreLabel, Modifier.size(22.dp))
                }
                if (expanded) Popup(
                    popupPositionProvider = positioner,
                    onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)
                ) {
                    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.widthIn(min = 160.dp, max = 280.dp).heightIn(max = menuHeight)
                            .verticalScroll(rememberScrollState())) {
                            placement.overflowIndices.forEach { index ->
                                val action = actions[index]
                                Row(Modifier.fillMaxWidth().clickable(enabled = action.enabled) {
                                    expanded = false
                                    action.onClick()
                                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(action.icon, null, Modifier.size(20.dp),
                                        tint = if (action.enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant)
                                    Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (action.enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A scrollable content surface, not a card. Background/return/backdrop stay in the existing host. */
@Composable
fun RisePatternPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<RiseTopAction> = emptyList(),
    bottomAction: (@Composable (Backdrop) -> Unit)? = null,
    windowInsets: WindowInsets = LocalRisePageInsets.current ?: WindowInsets.systemBars,
    content: @Composable ColumnScope.() -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val scrollState = rememberScrollState()
    val blurProgress = rememberTopBlurProgress(scrollState)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val room = maxWidth.value - 20f - 48f - 14f - 72f * fontScale.coerceAtLeast(1f) - 12f
        val slots = (room / 48f).toInt().coerceIn(1, 2)
        val placement = topActionPlacement(actions.size, slots)
        val visibleCount = placement.directIndices.size + if (placement.overflowIndices.isEmpty()) 0 else 1
        SecondaryPageScaffold(
            title, onBack, actions = { RiseTopActions(actions, slots) },
            topBarActionsWidth = (visibleCount * 48).dp, topBlurProgress = blurProgress,
            bottomAction = bottomAction, adaptiveBottomActionSpace = true, windowInsets = windowInsets
        ) { padding ->
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)
                .padding(padding).consumeWindowInsets(windowInsets).imePadding(),
                verticalArrangement = Arrangement.spacedBy(RiseUiSpacing.Group), content = content)
        }
    }
}
