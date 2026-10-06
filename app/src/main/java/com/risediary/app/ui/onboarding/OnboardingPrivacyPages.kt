/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.original.originalOnboardingSummary
import com.risediary.app.ui.settings.SettingsToggleItem
import top.yukonga.miuix.kmp.basic.Text

@Composable
private fun OnboardingToggle(
    icon: ImageVector, title: String, subtitle: String, checked: Boolean,
    enabled: Boolean, onCheckedChange: (Boolean) -> Unit,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChange by rememberUpdatedState(onCheckedChange)
    // SettingsToggleItem records the static row, with its glass switch as a sibling.
    SettingsToggleItem(icon, title, subtitle, checked,
        { if (currentEnabled) currentChange(it) }, enabled = enabled)
}

@Composable
internal fun PrivacyOnboardingPage(
    state: OnboardingUiState, scrollState: ScrollState, backdrop: Backdrop, enabled: Boolean,
    biometricAvailable: Boolean, onCreateLock: () -> Unit, onManageLock: (() -> Unit)?,
    onEnableBiometric: (Boolean) -> Unit, onVideoHidden: (Boolean) -> Unit,
) {
    OnboardingPage(stringResource(R.string.oobe_privacy_title), stringResource(R.string.oobe_privacy_subtitle), scrollState, icon = AppIcons.Lock) {
        OnboardingCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OnboardingStatus(
                    AppIcons.Lock,
                    stringResource(if (state.appLockEnabled) R.string.settings_app_lock_on else R.string.settings_app_lock_off),
                    stringResource(R.string.oobe_lock_summary),
                )
                if (!state.appLockEnabled || onManageLock != null) OnboardingAction(
                    stringResource(if (state.appLockEnabled) R.string.onboarding_manage_app_lock else R.string.onboarding_enable_app_lock),
                    AppIcons.Lock, backdrop, onManageLock?.takeIf { state.appLockEnabled } ?: onCreateLock,
                    enabled = enabled,
                )
                if (state.appLockEnabled) {
                    if (!state.biometricEnabled && !biometricAvailable)
                        Text(stringResource(R.string.oobe_biometric_unavailable),
                            fontSize = 14.sp, color = originalOnboardingSummary())
                }
            }
            if (state.appLockEnabled && (state.biometricEnabled || biometricAvailable)) OnboardingToggle(
                AppIcons.Fingerprint, stringResource(R.string.settings_biometric_unlock),
                stringResource(R.string.onboarding_app_lock_summary), state.biometricEnabled,
                enabled, onEnableBiometric,
            )
        }
        OnboardingCard(Modifier.fillMaxWidth()) {
            OnboardingToggle(
                AppIcons.VisibilityOff, stringResource(R.string.settings_detail_video_hidden),
                stringResource(R.string.oobe_video_hide_summary), state.detailVideoHidden, enabled, onVideoHidden,
            )
        }
    }
}

@Composable
internal fun NotificationsOnboardingPage(
    state: OnboardingUiState, scrollState: ScrollState, backdrop: Backdrop, enabled: Boolean,
    notificationsAllowed: Boolean, onEnableNotifications: () -> Unit,
    onLiveUpdates: (Boolean) -> Unit,
) {
    OnboardingPage(stringResource(R.string.oobe_notifications_title), stringResource(R.string.oobe_notifications_subtitle), scrollState, icon = AppIcons.NotificationsActive) {
        OnboardingCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OnboardingStatus(
                    AppIcons.NotificationsActive,
                    stringResource(if (notificationsAllowed) R.string.oobe_notifications_on else R.string.oobe_notifications_off),
                    stringResource(if (notificationsAllowed) R.string.oobe_notifications_on_summary else R.string.oobe_notifications_off_summary),
                )
                if (!notificationsAllowed) OnboardingAction(stringResource(R.string.oobe_enable_notifications),
                    AppIcons.NotificationsActive, backdrop, onEnableNotifications, enabled = enabled)
            }
        }
        OnboardingCard(Modifier.fillMaxWidth()) {
            OnboardingToggle(
                AppIcons.Schedule,
                stringResource(R.string.settings_live_updates),
                stringResource(R.string.settings_live_updates_summary),
                state.liveUpdatesEnabled, enabled, onLiveUpdates,
            )
        }
    }
}
