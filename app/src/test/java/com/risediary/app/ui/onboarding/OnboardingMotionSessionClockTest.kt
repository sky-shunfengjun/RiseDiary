package com.risediary.app.ui.onboarding

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class OnboardingMotionSessionClockTest {
    @Test fun completedEntranceDoesNotFreezeTheFlowingBackground() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset.Zero)
        motion.advanceBy(1800f)
        val time = motion.visualElapsedMillis
        assertTrue(motion.needsFrames)
        motion.advanceBy(400f)
        assertEquals(time + 400f, motion.visualElapsedMillis, 0.01f)
        assertEquals(1790f, motion.introElapsedMillis, 0f)
        motion.advanceBy(5000f, active = false)
        assertEquals(time + 400f, motion.visualElapsedMillis, 0.01f)
    }
    @Test fun everyNewTransitionGetsADifferentCompletionIdentity() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset.Zero, false)
        motion.observeStep(OnboardingStep.STATEMENT)
        val first = motion.transitionId
        motion.advanceBy(500f)
        motion.observeStep(OnboardingStep.PROFILE)
        assertTrue(motion.transitionId > first)
    }
}
