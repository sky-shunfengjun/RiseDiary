package com.risediary.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

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
    val systemBarAppearance = systemBarAppearance(darkTheme)
    if (!view.isInEditMode) {
        SideEffect {
            applySystemBarAppearance(view, systemBarAppearance)
        }
    }
    MiuixTheme(
        controller = controller,
        textStyles = defaultTextStyles(
            main = defaultTextStyles().main.copy(fontSize = 16.sp),
            paragraph = defaultTextStyles().paragraph.copy(fontSize = 14.sp),
            button = defaultTextStyles().button.copy(fontSize = 14.sp),
            title4 = defaultTextStyles().title4.copy(fontSize = 16.sp),
            body1 = defaultTextStyles().body1.copy(fontSize = 14.sp),
            body2 = defaultTextStyles().body2.copy(fontSize = 12.sp),
        ),
    ) {
        CompositionLocalProvider(LocalRiseDarkTheme provides darkTheme, content = content)
    }
}

@Composable
internal fun SystemBarIconOverride(forceLightIcons: Boolean) {
    val view = LocalView.current
    val darkTheme = LocalRiseDarkTheme.current
    val appearance = systemBarAppearance(
        darkTheme = darkTheme,
        forceLightIcons = forceLightIcons
    )
    if (!view.isInEditMode) {
        SideEffect {
            applySystemBarAppearance(view, appearance)
        }
        DisposableEffect(view, darkTheme, forceLightIcons) {
            onDispose {
                applySystemBarAppearance(view, systemBarAppearance(darkTheme))
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun applySystemBarAppearance(
    view: android.view.View,
    appearance: SystemBarAppearance
) {
    val window = (view.context as? Activity)?.window ?: return
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = AndroidColor.TRANSPARENT
    window.navigationBarColor = AndroidColor.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.navigationBarDividerColor = AndroidColor.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = appearance.lightStatusBarIcons
        isAppearanceLightNavigationBars = appearance.lightNavigationBarIcons
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
