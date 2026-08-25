package com.risediary.app.ui.theme

internal data class SystemBarAppearance(
    val lightStatusBarIcons: Boolean,
    val lightNavigationBarIcons: Boolean
)

internal fun systemBarAppearance(
    darkTheme: Boolean,
    forceLightIcons: Boolean = false
): SystemBarAppearance {
    val useLightIcons = darkTheme || forceLightIcons
    return SystemBarAppearance(
        lightStatusBarIcons = !useLightIcons,
        lightNavigationBarIcons = !useLightIcons
    )
}
