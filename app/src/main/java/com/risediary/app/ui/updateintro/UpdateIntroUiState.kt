package com.risediary.app.ui.updateintro

import com.risediary.app.ui.policy.PolicyDocument

enum class UpdateIntroStep { SUCCESS, STATEMENT, RECORDING_TIMER, NOTIFICATIONS, VIDEO_PRIVACY, COMPLETE }
enum class UpdateIntroMode { AUTO, REVIEW }
data class UpdateIntroUiState(
    val step: UpdateIntroStep = UpdateIntroStep.SUCCESS,
    val ready: Boolean = false,
    val loading: Boolean = true,
    val readError: String? = null,
    val saveError: String? = null,
    val actionError: Boolean = false,
    val saving: Boolean = false,
    val transitioning: Boolean = false,
    val finished: Boolean = false,
    val acceptedStatement: Boolean = false,
    val predictionMaxTicks: Int = 80,
    val liveUpdatesEnabled: Boolean = true,
    val detailVideoHidden: Boolean = false,
    val document: PolicyDocument? = null,
) {
    val modalOpen get() = document != null
    val canContinue get() = !saving && !transitioning && !finished && !modalOpen && when (step) {
        UpdateIntroStep.SUCCESS -> true
        UpdateIntroStep.STATEMENT -> acceptedStatement
        else -> ready
    }
}
