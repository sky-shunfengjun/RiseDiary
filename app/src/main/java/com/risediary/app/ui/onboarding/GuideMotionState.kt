/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import com.risediary.app.ui.onboarding.original.OriginalMotionCurves

/** One memory-session clock and ticket gate survive Activity recreation, never process death. */
internal class GuideMotionState(private val gate: GuideTransitionGate = GuideTransitionGate(), private val completePage: Int = 7) {
    var introElapsedMillis by mutableFloatStateOf(0f)
        private set
    var visualElapsedMillis by mutableFloatStateOf(0f)
        private set
    var introCenter by mutableStateOf(Offset.Unspecified)
        private set
    private var introStarted by mutableStateOf(false)
    var step by mutableStateOf(0)
        private set
    var fromStep by mutableStateOf(0)
        private set
    private var transitionElapsed by mutableFloatStateOf(0f)
    private var ticket by mutableStateOf<GuideTransitionTicket?>(null)
    val transitionId get() = ticket?.id ?: 0L
    var isTransitioning by mutableStateOf(false)
        private set
    var welcomeButtonBounds by mutableStateOf(Rect.Zero)
        private set
    private var viewport = Size.Zero

    val introRunning get() = introStarted && introElapsedMillis < OriginalMotionCurves.INTRO_DURATION
    val needsFrames get() = introRunning || isTransitioning ||
        (introStarted && (step == 0 || step == completePage))
    val canContinue get() = introStarted && !introRunning && !isTransitioning
    val arrowAlpha get() = OriginalMotionCurves.arrowAlpha(introElapsedMillis)
    val logoScale get() = OnboardingLogoPresentation.scale(introElapsedMillis)
    val logoAlpha get() = OnboardingLogoPresentation.alpha(introElapsedMillis)
    val isWelcomeExpansion get() = isTransitioning &&
        setOf(fromStep, step) == setOf(0, 1)
    private val duration get() = if (setOf(fromStep, step) == setOf(0, 1)) 500f else 350f
    val progress get() = if (!isTransitioning) 1f else FastOutSlowInEasing.transform((transitionElapsed / duration).coerceIn(0f, 1f))
    val welcomeExpansion get() = when {
        isWelcomeExpansion -> if (step == 1) progress else 1f - progress
        step == 0 -> 0f
        else -> 1f
    }
    val visibleSteps get() = if (isTransitioning) listOf(fromStep, step).sorted() else listOf(step)
    val visualFrame get() = GuideVisualFrame(visualElapsedMillis, !introStarted || introRunning,
        ticket?.from, step, transitionId, progress, isTransitioning)

    fun startIntro(center: Offset, animationsEnabled: Boolean = true) {
        if (!center.isSpecified || !center.x.isFinite() || !center.y.isFinite()) return
        introCenter = center
        if (introStarted) return
        introStarted = true
        if (!animationsEnabled) {
            introElapsedMillis = OriginalMotionCurves.INTRO_DURATION
            visualElapsedMillis = OriginalMotionCurves.INTRO_DURATION
        }
    }
    fun placeWelcomeButton(bounds: Rect) {
        if (bounds.width > 0f && bounds.height > 0f) welcomeButtonBounds = bounds
    }
    fun placeViewport(size: Size) {
        if (size.width <= 0f || size.height <= 0f) return
        if (viewport.width > 0f && viewport.height > 0f && viewport != size) {
            val sx = size.width / viewport.width; val sy = size.height / viewport.height
            welcomeButtonBounds = Rect(welcomeButtonBounds.left * sx, welcomeButtonBounds.top * sy,
                welcomeButtonBounds.right * sx, welcomeButtonBounds.bottom * sy)
            if (introCenter.isSpecified) introCenter = Offset(introCenter.x * sx, introCenter.y * sy)
        }
        viewport = size
    }
    fun observeStep(next: Int, animationsEnabled: Boolean = true) {
        if (next == step) return
        fromStep = step
        step = next
        ticket = gate.begin(fromStep, next)
        transitionElapsed = 0f
        isTransitioning = animationsEnabled
    }
    fun completeTransition(id: Long, target: Int): Boolean {
        val pending = ticket ?: return false
        if (isTransitioning || pending.id != id || pending.to != target || step != target) return false
        return gate.complete(pending)
    }
    fun cancelTransitions() { ticket?.let(gate::cancel); ticket = null; isTransitioning = false }
    fun advanceBy(deltaMillis: Float, active: Boolean = true, durationScale: Float = 1f) {
        if (!active) return
        if (durationScale <= 0f) {
            if (introStarted) {
                introElapsedMillis = OriginalMotionCurves.INTRO_DURATION
                visualElapsedMillis = maxOf(visualElapsedMillis, OriginalMotionCurves.INTRO_DURATION)
            }
            isTransitioning = false
            return
        }
        val delta = if (deltaMillis.isFinite()) deltaMillis.coerceAtLeast(0f) / durationScale else 0f
        if (introStarted && needsFrames) visualElapsedMillis += delta
        if (introRunning) introElapsedMillis = (introElapsedMillis + delta).coerceAtMost(OriginalMotionCurves.INTRO_DURATION)
        if (isTransitioning) {
            val end = duration
            transitionElapsed = (transitionElapsed + delta).coerceAtMost(end)
            if (transitionElapsed >= end) isTransitioning = false
        }
    }
}
