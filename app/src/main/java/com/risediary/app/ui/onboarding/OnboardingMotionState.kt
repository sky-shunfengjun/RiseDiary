/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.ui.geometry.*

internal class OnboardingMotionState(gate: OnboardingTransitionGate = OnboardingTransitionGate()) {
    internal val scene = GuideMotionState(gate.sceneGate)
    val introElapsedMillis get() = scene.introElapsedMillis
    val visualElapsedMillis get() = scene.visualElapsedMillis
    val introCenter get() = scene.introCenter
    val step get() = OnboardingStep.entries[scene.step]
    val fromStep get() = OnboardingStep.entries[scene.fromStep]
    val transitionId get() = scene.transitionId
    val isTransitioning get() = scene.isTransitioning
    val welcomeButtonBounds get() = scene.welcomeButtonBounds
    val introRunning get() = scene.introRunning
    val needsFrames get() = scene.needsFrames
    val canContinue get() = scene.canContinue
    val arrowAlpha get() = scene.arrowAlpha
    val logoScale get() = scene.logoScale
    val logoAlpha get() = scene.logoAlpha
    val isWelcomeExpansion get() = scene.isWelcomeExpansion
    val progress get() = scene.progress
    val welcomeExpansion get() = scene.welcomeExpansion
    val visibleSteps get() = scene.visibleSteps.map { OnboardingStep.entries[it] }
    val visualFrame get() = scene.visualFrame.let {
        OnboardingVisualFrame(it.introMillis, it.showAdmission, it.fromStep?.let { p -> OnboardingStep.entries[p] },
            OnboardingStep.entries[it.targetStep], it.transitionId, it.transitionProgress, it.transitioning)
    }
    fun startIntro(center: Offset, animationsEnabled: Boolean = true) = scene.startIntro(center, animationsEnabled)
    fun placeWelcomeButton(bounds: Rect) = scene.placeWelcomeButton(bounds)
    fun placeViewport(size: Size) = scene.placeViewport(size)
    fun observeStep(next: OnboardingStep, animationsEnabled: Boolean = true) = scene.observeStep(next.ordinal, animationsEnabled)
    fun completeTransition(id: Long, target: OnboardingStep) = scene.completeTransition(id, target.ordinal)
    fun cancelTransitions() = scene.cancelTransitions()
    fun advanceBy(deltaMillis: Float, active: Boolean = true, durationScale: Float = 1f) =
        scene.advanceBy(deltaMillis, active, durationScale)
}
