/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.runtime.*
import kotlinx.coroutines.isActive

/** Sole clock. Native scene owns the only completion callback after binding its final frame. */
@Composable
internal fun OnboardingMotionClock(motion: OnboardingMotionState, active: Boolean, durationScale: Float) {
    LaunchedEffect(motion, active, durationScale, motion.needsFrames, motion.transitionId) {
        if (!active || !motion.needsFrames) return@LaunchedEffect
        if (durationScale <= 0f) {
            motion.advanceBy(0f, durationScale = 0f)
            return@LaunchedEffect
        }
        var previous = withFrameNanos { it }
        while (isActive && motion.needsFrames) {
            val now = withFrameNanos { it }
            motion.advanceBy((now - previous) / 1_000_000f, durationScale = durationScale)
            previous = now
        }
    }
}
