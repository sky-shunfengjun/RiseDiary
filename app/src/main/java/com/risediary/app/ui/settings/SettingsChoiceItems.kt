package com.risediary.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsThemeDropdown(
    value: String,
    onSelect: (String) -> Unit
) {
    val systemLabel = stringResource(R.string.settings_theme_system)
    val lightLabel = stringResource(R.string.settings_theme_light)
    val darkLabel = stringResource(R.string.settings_theme_dark)
    val optionKeys = remember { listOf("system", "light", "dark") }
    val labels = remember(systemLabel, lightLabel, darkLabel) {
        listOf(systemLabel, lightLabel, darkLabel)
    }
    OverlayDropdownPreference(
        items = labels,
        selectedIndex = optionKeys.indexOf(value).coerceAtLeast(0),
        title = stringResource(R.string.settings_theme),
        startAction = { SettingsIcon(Icons.Default.Palette) },
        onSelectedIndexChange = { index -> optionKeys.getOrNull(index)?.let(onSelect) }
    )
}

@Composable
internal fun SettingsThemeItem(
    icon: ImageVector,
    title: String,
    value: String,
    onSelect: (String) -> Unit
) {
    val systemLabel = stringResource(R.string.settings_theme_system)
    val lightLabel = stringResource(R.string.settings_theme_light)
    val darkLabel = stringResource(R.string.settings_theme_dark)
    val optionKeys = remember { listOf("system", "light", "dark") }
    val options = remember(systemLabel, lightLabel, darkLabel) {
        listOf(
            LiquidSegmentOption(systemLabel, Icons.Default.Palette),
            LiquidSegmentOption(lightLabel, Icons.Default.Palette),
            LiquidSegmentOption(darkLabel, Icons.Default.Palette)
        )
    }
    val rowSurface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val rowBackdrop = rememberLayerBackdrop {
        drawRect(rowSurface)
        drawContent()
    }
    SettingsChoiceLayout(icon, title, Modifier.layerBackdrop(rowBackdrop)) {
        LiquidSegmentedControl(
            options = options,
            selectedIndex = optionKeys.indexOf(value).coerceAtLeast(0),
            onSelected = { optionKeys.getOrNull(it)?.let(onSelect) },
            backdrop = rowBackdrop,
            modifier = it,
            containerHeight = 40.dp,
            contentPadding = 3.dp,
            showIcons = false,
            labelFontSize = 12.sp
        )
    }
}

@Composable
internal fun SettingsVolumeModeItem(
    value: DefaultVolumeMode,
    onSelect: (DefaultVolumeMode) -> Unit
) {
    val millilitersLabel = stringResource(R.string.settings_volume_mode_milliliters)
    val spurtsLabel = stringResource(R.string.settings_volume_mode_spurts)
    val modes = remember { listOf(DefaultVolumeMode.MILLILITERS, DefaultVolumeMode.SPURTS) }
    val options = remember(millilitersLabel, spurtsLabel) {
        listOf(
            LiquidSegmentOption(millilitersLabel, Icons.Default.WaterDrop),
            LiquidSegmentOption(spurtsLabel, Icons.Default.Numbers)
        )
    }
    val rowSurface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val rowBackdrop = rememberLayerBackdrop {
        drawRect(rowSurface)
        drawContent()
    }
    SettingsChoiceLayout(
        icon = Icons.Default.WaterDrop,
        title = stringResource(R.string.settings_default_volume_mode),
        captureModifier = Modifier.layerBackdrop(rowBackdrop),
        summary = stringResource(R.string.settings_default_volume_mode_summary)
    ) {
        LiquidSegmentedControl(
            options = options,
            selectedIndex = modes.indexOf(value).coerceAtLeast(0),
            onSelected = { modes.getOrNull(it)?.let(onSelect) },
            backdrop = rowBackdrop,
            modifier = it,
            containerHeight = 40.dp,
            contentPadding = 3.dp,
            showIcons = false,
            labelFontSize = 12.sp
        )
    }
}

@Composable
internal fun SettingsBackgroundLockModeItem(
    value: BackgroundLockMode,
    onSelect: (BackgroundLockMode) -> Unit
) {
    val alwaysLabel = stringResource(R.string.settings_background_lock_always)
    val exceptTimerLabel = stringResource(R.string.settings_background_lock_except_timer)
    val modes = remember {
        listOf(BackgroundLockMode.ALWAYS, BackgroundLockMode.EXCEPT_WHILE_TIMER_ACTIVE)
    }
    val options = remember(alwaysLabel, exceptTimerLabel) {
        listOf(
            LiquidSegmentOption(alwaysLabel, Icons.Default.Lock),
            LiquidSegmentOption(exceptTimerLabel, Icons.Default.Timer)
        )
    }
    val rowSurface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val rowBackdrop = rememberLayerBackdrop {
        drawRect(rowSurface)
        drawContent()
    }
    SettingsChoiceLayout(
        icon = Icons.Default.Timer,
        title = stringResource(R.string.settings_background_lock_mode),
        captureModifier = Modifier.layerBackdrop(rowBackdrop),
        summary = stringResource(R.string.settings_background_lock_mode_summary)
    ) {
        LiquidSegmentedControl(
            options = options,
            selectedIndex = modes.indexOf(value).coerceAtLeast(0),
            onSelected = { modes.getOrNull(it)?.let(onSelect) },
            backdrop = rowBackdrop,
            modifier = it,
            containerHeight = 40.dp,
            contentPadding = 3.dp,
            showIcons = false,
            labelFontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsChoiceLayout(
    icon: ImageVector,
    title: String,
    captureModifier: Modifier,
    summary: String? = null,
    control: @Composable (Modifier) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(captureModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (summary != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(52.dp))
        }
        control(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}
