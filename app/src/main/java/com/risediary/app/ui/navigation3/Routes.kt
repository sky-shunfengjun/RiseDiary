/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Navigator 改编自 KernelSU-Style-UI-Kit（GPL-3.0-only）。
 */
package com.risediary.app.ui.navigation3

import top.yukonga.miuix.kmp.nav.core.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object ModeSelect : Route

    @Serializable
    data object Timer : Route

    @Serializable
    data class RecordForm(
        val isTimer: Boolean,
        val duration: Long,
        val startTime: Long,
    ) : Route

    @Serializable
    data class RecordDetail(val flightId: Long) : Route

    @Serializable
    data class RecordEdit(val flightId: Long) : Route

    @Serializable
    data object TagManager : Route

    @Serializable
    data object AchievementWall : Route

    @Serializable
    data object About : Route

    @Serializable
    data object ThirdPartyLibs : Route

    @Serializable
    data object CardOrder : Route

    @Serializable
    data object BackupRestore : Route

    @Serializable
    data object LengthHistory : Route

    @Serializable
    data object ReminderSettings : Route

    @Serializable
    data object AppLockSettings : Route

    @Serializable
    data object LockSetup : Route

    @Serializable
    data object LockChange : Route

    @Serializable
    data object LockDisable : Route

    @Serializable
    data object OnboardingReview : Route
}
