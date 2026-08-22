package com.risediary.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.settings.SettingsDivider
import com.risediary.app.ui.settings.SettingsReminderAccuracyNotice
import com.risediary.app.ui.settings.SettingsSliderItem
import com.risediary.app.ui.settings.SettingsToggleItem
import com.risediary.app.ui.settings.SettingsVolumeModeItem
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import com.risediary.app.ui.theme.CardOrange
import com.risediary.app.ui.theme.CardPurple
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.DecimalFormat

@Composable
internal fun RecordingOnboardingPage(
    volumeMode: DefaultVolumeMode,
    mlPerSpurt: Float,
    backdrop: Backdrop,
    onVolumeModeSelected: (DefaultVolumeMode) -> Unit,
    onMlPerSpurtChange: (Float) -> Unit
) {
    SetupPage(
        icon = Icons.Default.EditNote,
        title = stringResource(R.string.onboarding_recording_title),
        subtitle = stringResource(R.string.onboarding_recording_subtitle)
    ) {
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            SettingsVolumeModeItem(value = volumeMode, onSelect = onVolumeModeSelected)
            if (volumeMode == DefaultVolumeMode.SPURTS) {
                SettingsDivider()
                SettingsSliderItem(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_ml_conversion),
                    subtitle = stringResource(
                        R.string.settings_ml_conversion_summary,
                        DecimalFormat("0.#").format(mlPerSpurt)
                    ),
                    value = mlPerSpurt,
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChange = onMlPerSpurtChange
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureCard(
                Icons.Default.Timer,
                stringResource(R.string.onboarding_timer_title),
                stringResource(R.string.onboarding_timer_summary),
                CardBlue,
                Modifier.weight(1f)
            )
            FeatureCard(
                Icons.Default.EditNote,
                stringResource(R.string.onboarding_direct_title),
                stringResource(R.string.onboarding_direct_summary),
                CardPurple,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun PrivacyOnboardingPage(
    appLockEnabled: Boolean,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    recommendedRemindersEnabled: Boolean,
    reminderTime: String,
    backdrop: Backdrop,
    onCreateAppLock: () -> Unit,
    onManageAppLock: (() -> Unit)?,
    onEnableBiometric: () -> Unit,
    onRecommendedRemindersChange: (Boolean) -> Unit,
    onEditReminderTime: () -> Unit
) {
    SetupPage(
        icon = Icons.Default.Lock,
        title = stringResource(R.string.onboarding_privacy_setup_title),
        subtitle = stringResource(R.string.onboarding_privacy_setup_subtitle)
    ) {
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    Icons.Default.Lock,
                    stringResource(
                        if (appLockEnabled) R.string.settings_app_lock_on
                        else R.string.settings_app_lock_off
                    ),
                    stringResource(R.string.onboarding_app_lock_summary),
                    CardPurple
                )
                if (!appLockEnabled || onManageAppLock != null) {
                    Box(modifier = Modifier.padding(4.dp)) {
                        LiquidGlassButton(
                            onClick = onManageAppLock ?: onCreateAppLock,
                            backdrop = backdrop,
                            tint = CardPurple,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, null, tint = MiuixTheme.colorScheme.onPrimary)
                            Text(
                                stringResource(
                                    if (appLockEnabled) R.string.onboarding_manage_app_lock
                                    else R.string.onboarding_enable_app_lock
                                ),
                                color = MiuixTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (appLockEnabled && biometricAvailable && !biometricEnabled) {
                    Box(modifier = Modifier.padding(4.dp)) {
                        LiquidGlassButton(
                            onClick = onEnableBiometric,
                            backdrop = backdrop,
                            surfaceColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.48f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, null)
                            Text(stringResource(R.string.onboarding_enable_biometric))
                        }
                    }
                }
            }
        }
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleItem(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.onboarding_recommended_reminders),
                subtitle = stringResource(
                    R.string.onboarding_recommended_reminders_summary,
                    reminderTime
                ),
                checked = recommendedRemindersEnabled,
                onCheckedChange = onRecommendedRemindersChange
            )
            SettingsDivider()
            Box(modifier = Modifier.padding(16.dp)) {
                LiquidGlassButton(
                    onClick = onEditReminderTime,
                    backdrop = backdrop,
                    surfaceColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.48f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, null)
                    Text(
                        stringResource(R.string.onboarding_reminder_time, reminderTime),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                SettingsReminderAccuracyNotice()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFeature(
                Icons.Default.Dashboard,
                stringResource(R.string.onboarding_feature_dashboard),
                CardBlue,
                Modifier.weight(1f)
            )
            SmallFeature(
                Icons.Default.ShowChart,
                stringResource(R.string.onboarding_feature_trends),
                CardGreen,
                Modifier.weight(1f)
            )
            SmallFeature(
                Icons.Default.EmojiEvents,
                stringResource(R.string.onboarding_feature_achievements),
                CardOrange,
                Modifier.weight(1f)
            )
        }
    }
}
