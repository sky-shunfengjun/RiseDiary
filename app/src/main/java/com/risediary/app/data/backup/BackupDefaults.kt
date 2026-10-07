package com.risediary.app.data.backup

import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.BackgroundLockMode

internal fun defaultBackupSettings() = SettingsSnapshot(
        username = "机长",
        mlPerSpurt = 2f,
        defaultVolumeMode = DefaultVolumeMode.MILLILITERS,
        dailyReminderEnabled = false,
        dailyReminderTime = "22:00",
        inactiveReminderEnabled = false,
        inactiveReminderDays = 7,
        inactiveReminderTime = "22:00",
        monthlyLengthReminderEnabled = false,
        monthlyLengthReminderDay = 1,
        monthlyLengthReminderTime = "22:00",
        reminderSound = true,
        reminderVibration = true,
        backgroundAutoLockEnabled = false,
        backgroundLockMode = BackgroundLockMode.ALWAYS,
        themeMode = "system",
        homeCardOrder = "[]",
        homeCardVisibility = "{}",
        onboardingCompleted = false
    )
