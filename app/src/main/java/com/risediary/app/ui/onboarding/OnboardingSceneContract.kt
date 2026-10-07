/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

internal enum class IntroBackend { ORIGINAL_SHADER, CANVAS, STATIC }

/** introMillis is the continuous visible-session clock; individual entrance curves clamp it. */
internal data class OnboardingVisualFrame(
    val introMillis: Float,
    val showAdmission: Boolean,
    val fromStep: OnboardingStep?,
    val targetStep: OnboardingStep,
    val transitionId: Long,
    val transitionProgress: Float,
    val transitioning: Boolean,
) {
    val logoAlpha get() = OnboardingLogoPresentation.alpha(introMillis)
    val logoScale get() = OnboardingLogoPresentation.scale(introMillis)
}

/** Keep upstream curves intact; let the wave lead the logo/name by 200ms. */
internal object OnboardingLogoPresentation {
    private fun clock(millis: Float) = (millis - 200f).coerceAtLeast(0f)
    fun alpha(millis: Float) = com.risediary.app.ui.onboarding.original.OriginalMotionCurves.logoAlpha(clock(millis))
    fun scale(millis: Float) = com.risediary.app.ui.onboarding.original.OriginalMotionCurves.logoScale(clock(millis))
}

internal data class OnboardingTransitionTicket(val id: Long, val from: OnboardingStep, val to: OnboardingStep)

internal data class NativeRevealGeometry(
    val centerX: Float, val centerY: Float, val initialRadius: Float, val finalRadius: Float,
) {
    val isUsable: Boolean get() = centerX.isFinite() && centerY.isFinite() && initialRadius.isFinite() &&
        finalRadius.isFinite() && initialRadius > 0f && finalRadius >= initialRadius
}
