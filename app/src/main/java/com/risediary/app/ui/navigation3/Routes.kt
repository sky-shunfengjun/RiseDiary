/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Navigator 改编自 KernelSU-Style-UI-Kit（GPL-3.0-only）。
 */
package com.risediary.app.ui.navigation3

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize

sealed interface Route : NavKey, Parcelable {
    @Parcelize
    data object Main : Route

    @Parcelize
    data object ModeSelect : Route

    @Parcelize
    data object Timer : Route

    @Parcelize
    data class RecordForm(
        val isTimer: Boolean,
        val duration: Long,
        val startTime: Long,
    ) : Route

    @Parcelize
    data class RecordDetail(val flightId: Long) : Route

    @Parcelize
    data class RecordEdit(val flightId: Long) : Route

    @Parcelize
    data object TagManager : Route

    @Parcelize
    data object AchievementWall : Route

    @Parcelize
    data object About : Route

    @Parcelize
    data object ThirdPartyLibs : Route

    @Parcelize
    data object CardOrder : Route

    @Parcelize
    data object BackupRestore : Route

    @Parcelize
    data object LengthHistory : Route

    @Parcelize
    data object ReminderSettings : Route

    @Parcelize
    data object AppLockSettings : Route

    @Parcelize
    data object LockSetup : Route

    @Parcelize
    data object LockChange : Route

    @Parcelize
    data object LockDisable : Route

    @Parcelize
    data object OnboardingReview : Route
}
