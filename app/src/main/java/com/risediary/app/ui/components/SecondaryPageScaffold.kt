/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.theme.backgroundBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

/**
 * 二级页面骨架：沉浸式边界 + 液体玻璃返回键 + 标题顶栏。
 */
@Composable
fun SecondaryPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: (@Composable (Backdrop) -> Unit)? = null,
    reserveFloatingActionButtonSpace: Boolean = true,
    bottomAction: (@Composable (Backdrop) -> Unit)? = null,
    reserveBottomActionSpace: Boolean = true,
    titleAlpha: (() -> Float)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val statusBarTopDp = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val navigationBarBottomDp = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val pageBackground = backgroundBrush()
    val currentBackground by rememberUpdatedState(pageBackground)
    val backdrop = rememberLayerBackdrop {
        drawRect(brush = currentBackground)
        drawContent()
    }
    val backScope = rememberCoroutineScope()
    var backThrottled by remember { mutableStateOf(false) }
    val guardedBack: () -> Unit = {
        if (!backThrottled) {
            backThrottled = true
            onBack()
            backScope.launch {
                delay(450)
                backThrottled = false
            }
        }
    }

    ProvidePageBackdrop(backdrop) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(pageBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                content(
                    PaddingValues(
                        start = 20.dp,
                        top = statusBarTopDp + 76.dp,
                        end = 20.dp,
                        bottom = navigationBarBottomDp +
                            if (
                                (
                                    floatingActionButton != null &&
                                        reserveFloatingActionButtonSpace
                                    ) || (bottomAction != null && reserveBottomActionSpace)
                            ) {
                                104.dp
                            } else {
                                20.dp
                            }
                    )
                )
            }

            snackbarHost()

            PageTopBar(
                title = title,
                onBack = guardedBack,
                backdrop = backdrop,
                actions = actions,
                titleAlpha = titleAlpha
            )

            if (floatingActionButton != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 20.dp)
                ) {
                    floatingActionButton(backdrop)
                }
            }

            if (bottomAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    bottomAction(backdrop)
                }
            }
        }
    }
}

/**
 * 共享顶栏：液体玻璃返回键 + 标题（30sp SemiBold 左对齐）。
 */
@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    titleAlpha: (() -> Float)? = null,
    endPadding: Dp = 12.dp
) {
    val baseAlpha = titleAlpha?.invoke() ?: 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(start = 20.dp, end = endPadding)
    ) {
        LiquidBackButton(
            onClick = onBack,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 62.dp)
                .graphicsLayer { alpha = baseAlpha },
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

@Composable
fun LiquidAddButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    LiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(64.dp),
        tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.035f),
        height = 64.dp,
        horizontalPadding = 0.dp,
        highlightIntensity = 0.36f,
        highlightRadiusMultiplier = 0.98f,
        pressExpansion = 2.dp
    ) {
        Icon(
            imageVector = AppIcons.Add,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun LiquidBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    LiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(48.dp),
        tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.035f),
        height = 48.dp,
        horizontalPadding = 0.dp,
        highlightIntensity = 0.34f,
        highlightRadiusMultiplier = 0.95f,
        pressExpansion = 2.dp
    ) {
        Icon(
            imageVector = AppIcons.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
