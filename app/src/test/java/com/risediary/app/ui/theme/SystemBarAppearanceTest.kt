package com.risediary.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarAppearanceTest {

    @Test
    fun light_theme_uses_dark_system_bar_icons() {
        assertEquals(
            SystemBarAppearance(lightStatusBarIcons = true, lightNavigationBarIcons = true),
            systemBarAppearance(darkTheme = false)
        )
    }

    @Test
    fun dark_theme_uses_light_system_bar_icons() {
        assertEquals(
            SystemBarAppearance(lightStatusBarIcons = false, lightNavigationBarIcons = false),
            systemBarAppearance(darkTheme = true)
        )
    }

    @Test
    fun dark_overlay_forces_light_icons_even_in_light_app_theme() {
        assertEquals(
            SystemBarAppearance(lightStatusBarIcons = false, lightNavigationBarIcons = false),
            systemBarAppearance(darkTheme = false, forceLightIcons = true)
        )
    }
}
