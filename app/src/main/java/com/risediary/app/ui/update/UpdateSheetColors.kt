package com.risediary.app.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Native gray dialog surface in dark mode; preserve the existing light surface. */
@Composable
internal fun updateSheetBackground(): Color = if (LocalRiseDarkTheme.current) {
    MiuixTheme.colorScheme.background
} else {
    MiuixTheme.colorScheme.surface
}

/** Keep grouped settings visibly above the sheet background in both themes. */
@Composable
internal fun updateSheetCardColor(): Color = if (LocalRiseDarkTheme.current) {
    MiuixTheme.colorScheme.surfaceContainerHighest
} else {
    MiuixTheme.colorScheme.surfaceContainer
}
