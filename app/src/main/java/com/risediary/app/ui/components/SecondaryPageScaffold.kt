package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.theme.backgroundBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared frame for secondary and tertiary pages.
 *
 * The content is captured in its own backdrop layer while the glass controls
 * are drawn as siblings above it. This keeps the glass button from capturing
 * itself and matches the layering used by AndroidLiquidGlass's demo.
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
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val statusBarTopPx = with(density) {
        WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding()
            .toPx()
    }
    val windowHeightPx = LocalView.current.height.toFloat()
    val pageBackground = backgroundBrush(
        topInsetPx = statusBarTopPx,
        windowHeightPx = windowHeightPx
    )
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
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = snackbarHost,
            topBar = {
                TopAppBar(
                    title = title,
                    color = Color.Transparent,
                    titleColor = MiuixTheme.colorScheme.onSurface,
                    navigationIcon = {
                        LiquidBackButton(
                            onClick = guardedBack,
                            backdrop = backdrop,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    actions = { actions() },
                    defaultWindowInsetsPadding = false
                )
            }
        ) { scaffoldPadding ->
            content(
                PaddingValues(
                    start = 20.dp,
                    top = scaffoldPadding.calculateTopPadding() + 16.dp,
                    end = 20.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() +
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

        if (floatingActionButton != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            ) {
                floatingActionButton(backdrop)
            }
        }

        if (bottomAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The activity no longer relies on adjustResize; IME insets are
                    // consumed once here so the bottom action floats above the keyboard.
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                bottomAction(backdrop)
            }
        }
        }
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
            imageVector = Icons.Default.Add,
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
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
