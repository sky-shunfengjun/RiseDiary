package com.risediary.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class RiseDiaryThemeTest {

    @Test
    fun themeMode_maps_to_colorSchemeMode() {
        assertEquals(ColorSchemeMode.Light, themeModeToColorSchemeMode("light"))
        assertEquals(ColorSchemeMode.Dark, themeModeToColorSchemeMode("dark"))
        assertEquals(ColorSchemeMode.System, themeModeToColorSchemeMode("system"))
        assertEquals(ColorSchemeMode.System, themeModeToColorSchemeMode("未知值"))
        assertEquals(ColorSchemeMode.System, themeModeToColorSchemeMode(""))
    }
}