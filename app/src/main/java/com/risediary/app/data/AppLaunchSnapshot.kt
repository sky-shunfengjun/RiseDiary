package com.risediary.app.data

/** Fixed product campaign: a new Dev build must never be another update introduction. */
object UpdateIntroCampaign { const val ID = "2.0.0" }

data class AppLaunchSnapshot(
    val security: SecuritySettingsSnapshot,
    val lastCompletedUpdateIntroId: String?,
) {
    val updateIntroPending: Boolean get() =
        security.onboardingCompleted && lastCompletedUpdateIntroId != UpdateIntroCampaign.ID
}

/** Strict read, containing only editable guide preferences and no credentials. */
data class UpdateIntroSettingsSnapshot(
    val predictionMaxTicks: Int,
    val liveUpdatesEnabled: Boolean,
    val detailVideoHiddenByDefault: Boolean,
)
