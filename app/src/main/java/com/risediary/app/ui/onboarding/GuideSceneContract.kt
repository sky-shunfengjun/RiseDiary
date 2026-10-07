package com.risediary.app.ui.onboarding

import com.risediary.app.R

/** The scene contains presentation state only; guide-specific input stays in its ViewModel. */
internal data class GuideSceneUiState(
    val saving: Boolean = false,
    val transitioning: Boolean = false,
    val modalOpen: Boolean = false,
    val canContinue: Boolean = false,
)
internal enum class GuideWelcomeGraphic { APP_ICON, UPDATE_CHECK }
internal data class GuideSceneSpec(
    val completePage: Int = 7,
    val welcomeGraphic: GuideWelcomeGraphic = GuideWelcomeGraphic.APP_ICON,
    val welcomeTitle: Int = R.string.app_name,
    val welcomeSubtitle: Int = R.string.oobe_welcome_subtitle,
    val statementButton: Int? = null,
    val immersiveBody: Boolean = false,
)
internal data class GuideVisualFrame(
    val introMillis: Float,
    val showAdmission: Boolean,
    val fromStep: Int?,
    val targetStep: Int,
    val transitionId: Long,
    val transitionProgress: Float,
    val transitioning: Boolean,
) {
    val logoAlpha get() = OnboardingLogoPresentation.alpha(introMillis)
    val logoScale get() = OnboardingLogoPresentation.scale(introMillis)
}
internal fun OnboardingVisualFrame.toGuideFrame() = GuideVisualFrame(
    introMillis, showAdmission, fromStep?.ordinal, targetStep.ordinal,
    transitionId, transitionProgress, transitioning,
)
internal data class GuideTransitionTicket(val id: Long, val from: Int, val to: Int)
internal class GuideTransitionGate {
    private var sequence = 0L
    private var current: GuideTransitionTicket? = null
    fun begin(from: Int, to: Int) = GuideTransitionTicket(++sequence, from, to).also { current = it }
    fun complete(ticket: GuideTransitionTicket): Boolean {
        if (current != ticket) return false
        current = null
        return true
    }
    fun cancel(ticket: GuideTransitionTicket) = complete(ticket)
}
