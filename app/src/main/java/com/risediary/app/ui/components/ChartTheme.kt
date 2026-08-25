package com.risediary.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.risediary.app.ui.theme.DarkBackgroundStart
import com.risediary.app.ui.theme.DarkOnSurface
import com.risediary.app.ui.theme.DarkOnSurfaceSecondary
import com.risediary.app.ui.theme.DarkPrimary
import com.risediary.app.ui.theme.DarkSurface
import com.risediary.app.ui.theme.DarkSurfaceBorder
import com.risediary.app.ui.theme.DarkSurfaceVariant
import com.risediary.app.ui.theme.LightBackgroundStart
import com.risediary.app.ui.theme.LightOnSurface
import com.risediary.app.ui.theme.LightOnSurfaceSecondary
import com.risediary.app.ui.theme.LightPrimary
import com.risediary.app.ui.theme.LightSurface
import com.risediary.app.ui.theme.LightSurfaceBorder
import com.risediary.app.ui.theme.LightSurfaceVariant
import com.risediary.app.ui.theme.LocalRiseDarkTheme

@Composable
internal fun ChartTheme(content: @Composable () -> Unit) {
    val dark = LocalRiseDarkTheme.current
    val colorScheme = if (dark) {
        darkColorScheme(
            background = DarkBackgroundStart,
            surface = DarkSurface,
            surfaceContainerHighest = DarkSurfaceVariant,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceSecondary,
            primary = DarkPrimary,
            outline = DarkSurfaceBorder
        )
    } else {
        lightColorScheme(
            background = LightBackgroundStart,
            surface = LightSurface,
            surfaceContainerHighest = LightSurfaceVariant,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceSecondary,
            primary = LightPrimary,
            outline = LightSurfaceBorder
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
