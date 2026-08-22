package com.risediary.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.BuildConfig
import com.risediary.app.R
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.UsernamePolicy
import com.risediary.app.ui.Screen
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.DecimalFormat

@Composable
fun SettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = hiltViewModel()
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        overscrollEffect = null
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MiuixTheme.textStyles.title1.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }

        item { SettingsGroupHeader(stringResource(R.string.settings_group_personal)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val username by vm.username.collectAsStateWithLifecycle()
                    SettingsEditItem(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.settings_username),
                        subtitle = stringResource(R.string.settings_username_summary),
                        value = username,
                        onValueChange = vm::setUsername,
                        placeholder = stringResource(R.string.settings_username_placeholder),
                        inputTransform = UsernamePolicy::limit
                    )
                    val defaultVolumeMode by vm.defaultVolumeMode.collectAsStateWithLifecycle()
                    SettingsVolumeModeItem(
                        value = defaultVolumeMode,
                        onSelect = vm::setDefaultVolumeMode
                    )
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
                    ArrowPreference(
                        title = stringResource(R.string.settings_manage_tags),
                        summary = stringResource(R.string.settings_manage_tags_summary),
                        startAction = { SettingsIcon(Icons.Default.LocalOffer) },
                        onClick = { navController.navigate(Screen.TagManager.route) }
                    )
                }
            }
        }

        item { SettingsGroupHeader(stringResource(R.string.settings_group_reminders)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_reminder_settings),
                    summary = stringResource(R.string.settings_reminder_settings_summary),
                    startAction = { SettingsIcon(Icons.Default.NotificationsActive) },
                    onClick = { navController.navigate(Screen.ReminderSettings.route) }
                )
            }
        }

        item { SettingsGroupHeader(stringResource(R.string.settings_group_privacy)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                val lockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
                ArrowPreference(
                    title = stringResource(R.string.settings_app_lock),
                    summary =
                        if (lockEnabled) stringResource(R.string.settings_app_lock_on)
                        else stringResource(R.string.settings_app_lock_off),
                    startAction = { SettingsIcon(Icons.Default.Lock) },
                    onClick = { navController.navigate(Screen.AppLockSettings.route) }
                )
            }
        }

        item { SettingsGroupHeader(stringResource(R.string.settings_group_data)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_backup),
                    summary = stringResource(R.string.settings_backup_summary),
                    startAction = { SettingsIcon(Icons.Default.Backup) },
                    onClick = { navController.navigate(Screen.BackupRestore.route) }
                )
            }
        }

        item { SettingsGroupHeader(stringResource(R.string.settings_group_display)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val theme by vm.themeMode.collectAsStateWithLifecycle()
                    SettingsThemeDropdown(
                        value = theme,
                        onSelect = vm::setThemeMode
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_card_order),
                        summary = stringResource(R.string.settings_card_order_summary),
                        startAction = { SettingsIcon(Icons.Default.Reorder) },
                        onClick = { navController.navigate(Screen.CardOrder.route) }
                    )
                }
            }
        }

        item { SettingsGroupHeader(stringResource(R.string.settings_group_about)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.settings_review_onboarding),
                        summary = stringResource(R.string.settings_review_onboarding_summary),
                        startAction = { SettingsIcon(Icons.Default.School) },
                        onClick = { navController.navigate("onboarding_review") }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        startAction = { SettingsIcon(Icons.Default.Info) },
                        onClick = { navController.navigate(Screen.About.route) }
                    )
                }
            }
        }
    }
}
