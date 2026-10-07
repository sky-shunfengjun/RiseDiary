/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.data

/** Strict settings read for the guide. Contains no unlock credential. */
data class OnboardingSettingsSnapshot(
    val username: String,
    val themeMode: String,
    val predictionMaxTicks: Int,
    val detailVideoHiddenByDefault: Boolean,
    val dailyReminderEnabled: Boolean,
    val inactiveReminderEnabled: Boolean,
    val inactiveReminderDays: Int,
    val inactiveReminderTime: String,
    val reminderTime: String,
    val appLockEnabled: Boolean,
    val biometricEnabled: Boolean,
    val liveUpdatesEnabled: Boolean = true,
) {
    val customReminders: Boolean get() = (dailyReminderEnabled || inactiveReminderEnabled) &&
        !(dailyReminderEnabled && inactiveReminderEnabled && inactiveReminderDays == 7 && reminderTime == inactiveReminderTime)
}
