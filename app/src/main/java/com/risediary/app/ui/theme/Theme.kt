package com.risediary.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalRiseDarkTheme = staticCompositionLocalOf { false }

internal fun themeModeToColorSchemeMode(mode: String): ColorSchemeMode = when (mode) {
    "light" -> ColorSchemeMode.Light
    "dark" -> ColorSchemeMode.Dark
    else -> ColorSchemeMode.System
}

@Composable
fun RiseDiaryTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val colorSchemeMode = remember(themeMode) { themeModeToColorSchemeMode(themeMode) }
    val controller = remember(colorSchemeMode) { ThemeController(colorSchemeMode = colorSchemeMode) }
    val darkTheme = when (colorSchemeMode) {
        ColorSchemeMode.Light -> false
        ColorSchemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalRiseDarkTheme provides darkTheme, content = content)
    }
}

@Composable
fun backgroundBrush(
    topInsetPx: Float = 0f,
    windowHeightPx: Float = Float.NaN
): Brush {
    val dark = LocalRiseDarkTheme.current
    val background = if (dark) DarkBackgroundStart else LightBackgroundStart
    val backgroundEnd = if (dark) DarkBackgroundEnd else LightBackgroundEnd
    val colors = listOf(background, backgroundEnd)
    if (windowHeightPx.isNaN()) {
        return Brush.verticalGradient(colors = colors)
    }
    val (startY, endY) = anchoredBackgroundEnds(topInsetPx, windowHeightPx)
    return Brush.verticalGradient(
        colors = colors,
        startY = startY,
        endY = endY
    )
}

internal fun anchoredBackgroundEnds(
    topInsetPx: Float,
    windowHeightPx: Float
): Pair<Float, Float> {
    val startY = if (topInsetPx <= 0f) 0f else -topInsetPx
    return startY to (windowHeightPx - topInsetPx)
}
