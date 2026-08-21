package com.risediary.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalRiseDarkTheme = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF173A5D),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF183743),
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Color(0xFF123D38),
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceSecondary,
    background = LightBackgroundStart,
    onBackground = LightOnSurface,
    outline = LightSurfaceBorder,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF264B6E),
    onPrimaryContainer = Color(0xFFD6E9FA),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF173540),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFD5EDF7),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF103E39),
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = Color(0xFFC4F1EB),
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceSecondary,
    background = DarkBackgroundStart,
    onBackground = DarkOnSurface,
    outline = DarkSurfaceBorder,
)

@Composable
fun RiseDiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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
    CompositionLocalProvider(LocalRiseDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content
        )
    }
}

@Composable
fun backgroundBrush(
    topInsetPx: Float = 0f,
    windowHeightPx: Float = Float.NaN
): Brush {
    val background = MaterialTheme.colorScheme.background
    val backgroundEnd =
        if (background == DarkBackgroundStart) DarkBackgroundEnd
        else LightBackgroundEnd
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
