package com.risediary.app.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.BuildConfig
import com.risediary.app.R
import com.risediary.app.RiseDiaryApp
import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.reminder.ReminderConfiguration
import com.risediary.app.ui.Screen
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.ui.components.LiquidSlider
import com.risediary.app.ui.components.LiquidToggle
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.risediary.app.ui.theme.RiseCard
import java.text.DecimalFormat

@Composable
fun SettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        SettingsGroupHeader(stringResource(R.string.settings_group_personal))
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            val username by vm.username.collectAsStateWithLifecycle()
            SettingsEditItem(
                icon = Icons.Default.Person,
                title = stringResource(R.string.settings_username),
                subtitle = stringResource(R.string.settings_username_summary),
                value = username,
                onValueChange = vm::setUsername,
                placeholder = stringResource(R.string.settings_username_placeholder)
            )
            SettingsDivider()
            val defaultVolumeMode by vm.defaultVolumeMode.collectAsStateWithLifecycle()
            SettingsVolumeModeItem(
                value = defaultVolumeMode,
                onSelect = vm::setDefaultVolumeMode
            )
            if (defaultVolumeMode == DefaultVolumeMode.SPURTS) {
                SettingsDivider()
            }
            val mlPerSpurt by vm.mlPerSpurt.collectAsStateWithLifecycle()
            if (defaultVolumeMode == DefaultVolumeMode.SPURTS) {
                SettingsSliderItem(
                    icon = Icons.Default.WaterDrop,
                    title = stringResource(R.string.settings_ml_conversion),
                    subtitle = stringResource(
                        R.string.settings_ml_conversion_summary,
                        DecimalFormat("0.#").format(mlPerSpurt)
                    ),
                    value = mlPerSpurt,
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChange = vm::setMlPerSpurt
                )
            }
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Default.LocalOffer,
                title = stringResource(R.string.settings_manage_tags),
                subtitle = stringResource(R.string.settings_manage_tags_summary),
                onClick = { navController.navigate(Screen.TagManager.route) }
            )
        }

        SettingsGroupHeader(stringResource(R.string.settings_group_reminders))
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            SettingsNavItem(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.settings_reminder_settings),
                subtitle = stringResource(R.string.settings_reminder_settings_summary),
                onClick = { navController.navigate(Screen.ReminderSettings.route) }
            )
        }

        SettingsGroupHeader(stringResource(R.string.settings_group_privacy))
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            val lockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
            SettingsNavItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_app_lock),
                subtitle =
                    if (lockEnabled) stringResource(R.string.settings_app_lock_on)
                    else stringResource(R.string.settings_app_lock_off),
                onClick = { navController.navigate(Screen.AppLockSettings.route) }
            )
        }

        SettingsGroupHeader(stringResource(R.string.settings_group_data))
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            SettingsNavItem(
                icon = Icons.Default.Backup,
                title = stringResource(R.string.settings_backup),
                subtitle = stringResource(R.string.settings_backup_summary),
                onClick = { navController.navigate(Screen.BackupRestore.route) }
            )
        }

        SettingsGroupHeader(stringResource(R.string.settings_group_display))
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            SettingsThemeItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_theme),
                value = theme,
                onSelect = vm::setThemeMode
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Default.Reorder,
                title = stringResource(R.string.settings_card_order),
                subtitle = stringResource(R.string.settings_card_order_summary),
                onClick = { navController.navigate(Screen.CardOrder.route) }
            )
        }

        SettingsGroupHeader(stringResource(R.string.settings_group_about))
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            SettingsNavItem(
                icon = Icons.Default.School,
                title = stringResource(R.string.settings_review_onboarding),
                subtitle = stringResource(R.string.settings_review_onboarding_summary),
                onClick = { navController.navigate("onboarding_review") }
            )
            SettingsDivider()
            SettingsNavItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                onClick = { navController.navigate(Screen.About.route) }
            )
        }

        Spacer(modifier = Modifier.height(88.dp))
    }

}
