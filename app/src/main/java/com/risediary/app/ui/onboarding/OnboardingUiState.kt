/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import com.risediary.app.ui.policy.PolicyDocument

enum class OnboardingStep { WELCOME, STATEMENT, PROFILE, THEME, PREDICTION, PRIVACY, NOTIFICATIONS, COMPLETE }
enum class OnboardingPermissionAction { NOTIFICATIONS }

/** Session-only state: deliberately has no SavedStateHandle or saved-state serializer. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val ready: Boolean = false,
    val loading: Boolean = true,
    val readError: String? = null,
    val saveError: String? = null,
    val actionError: OnboardingPermissionAction? = null,
    val saving: Boolean = false,
    val transitioning: Boolean = false,
    val finished: Boolean = false,
    val acceptedStatement: Boolean = false,
    val username: String = "机长",
    val themeMode: String = "",
    val predictionMaxTicks: Int = 80,
    val detailVideoHidden: Boolean = false,
    val liveUpdatesEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val document: PolicyDocument? = null,
    val lockSetup: Boolean = false,
) {
    val modalOpen: Boolean get() = document != null || lockSetup
    val canContinue: Boolean get() = !saving && !transitioning && !finished && !modalOpen && when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.STATEMENT -> acceptedStatement
        else -> ready
    }
}
