package com.risediary.app.ui.onboarding

import androidx.compose.ui.geometry.Offset
import com.risediary.app.ui.onboarding.original.OriginalMotionCurves
import org.junit.Assert.*
import org.junit.Test

class OnboardingMotionTest {
    @Test fun logoWaitsForTheWaveAndSharesThePausedSessionClock() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset(100f, 180f))
        motion.advanceBy(240f)
        assertEquals("The wave must be visible before the logo fades in", 0f, motion.logoAlpha, 0f)
        motion.advanceBy(20_000f, active = false)
        assertEquals(0f, motion.logoAlpha, 0f)
        motion.advanceBy(220f)
        assertEquals(2f / 3f, motion.logoAlpha, 0.001f)
        motion.startIntro(Offset(180f, 100f))
        assertEquals(2f / 3f, motion.logoAlpha, 0.001f)
        motion.advanceBy(0f, durationScale = 0f)
        assertEquals(1f, motion.logoAlpha, 0f)
        assertEquals(1f, motion.logoScale, 0f)
    }
    @Test fun backgroundDoesNotConsumeEntrance() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset(100f, 180f))
        motion.advanceBy(400f)
        motion.advanceBy(50_000f, active = false)
        assertEquals(400f, motion.introElapsedMillis, 0f)
        assertFalse(motion.canContinue)
        motion.advanceBy(1_400f)
        assertTrue(motion.canContinue)
    }
    @Test fun returnAndRotationDoNotReplayWave() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset(100f, 180f))
        motion.advanceBy(600f)
        motion.startIntro(Offset(180f, 100f))
        assertEquals(600f, motion.introElapsedMillis, 0f)
        assertEquals(Offset(180f, 100f), motion.introCenter)
        motion.advanceBy(1_200f)
        motion.observeStep(OnboardingStep.STATEMENT)
        motion.advanceBy(500f)
        motion.observeStep(OnboardingStep.WELCOME)
        motion.advanceBy(500f)
        motion.startIntro(Offset.Zero)
        assertEquals(1f, motion.arrowAlpha, 0f)
        assertFalse(motion.introRunning)
    }
    @Test fun disabledAnimationSettlesWithoutAFrameDelay() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset.Zero)
        motion.advanceBy(0f, durationScale = 0f)
        assertTrue(motion.canContinue)
        motion.observeStep(OnboardingStep.STATEMENT)
        motion.advanceBy(0f, durationScale = 0f)
        assertFalse(motion.isTransitioning)
        assertEquals(listOf(OnboardingStep.STATEMENT), motion.visibleSteps)
    }
    @Test fun welcomeExpansionKeepsOutgoingPageAndBlocksInputUntilSettled() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset.Zero, animationsEnabled = false)
        motion.observeStep(OnboardingStep.STATEMENT)
        motion.advanceBy(250f)
        assertTrue(motion.isWelcomeExpansion)
        assertFalse(motion.canContinue)
        assertEquals(listOf(OnboardingStep.WELCOME, OnboardingStep.STATEMENT), motion.visibleSteps)
        assertTrue(motion.welcomeExpansion in 0.01f..0.99f)
        motion.advanceBy(250f)
        assertTrue(motion.canContinue)
        assertEquals(listOf(OnboardingStep.STATEMENT), motion.visibleSteps)
    }
    @Test fun reverseExpansionContractsToWelcome() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset.Zero, animationsEnabled = false)
        motion.observeStep(OnboardingStep.STATEMENT, animationsEnabled = false)
        motion.observeStep(OnboardingStep.WELCOME)
        assertEquals(1f, motion.welcomeExpansion, 0f)
        motion.advanceBy(500f)
        assertEquals(0f, motion.welcomeExpansion, 0f)
        assertFalse(motion.isTransitioning)
    }
    @Test fun ordinaryPagesUseTheirOwn350msTransition() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset.Zero, animationsEnabled = false)
        motion.observeStep(OnboardingStep.STATEMENT, animationsEnabled = false)
        motion.observeStep(OnboardingStep.PROFILE)
        motion.advanceBy(349f)
        assertTrue(motion.isTransitioning)
        assertFalse(motion.isWelcomeExpansion)
        motion.advanceBy(1f)
        assertEquals(listOf(OnboardingStep.PROFILE), motion.visibleSteps)
    }
    @Test fun waveExpandsThenEndsButBackgroundStaysRevealed() {
        val early = OriginalMotionCurves.circleFrame(0.2f)
        val late = OriginalMotionCurves.circleFrame(0.8f)
        assertTrue(early.ringVisible)
        assertTrue(late.outerRadius > early.outerRadius)
        assertFalse(OriginalMotionCurves.circleFrame(1.12f).ringVisible)
        assertEquals(1f, OriginalMotionCurves.circleFrame(1.45f).maskRadius, 0.001f)
    }
    @Test fun maskExposesCenterBeforeEdges() {
        assertTrue(OriginalMotionCurves.circleFrame(0f).maskRadius < -0.3f)
        assertTrue(OriginalMotionCurves.circleFrame(0.8f).maskRadius > OriginalMotionCurves.circleFrame(0.2f).maskRadius)
        assertEquals(1f, OriginalMotionCurves.circleFrame(1.5f).maskRadius, 0.001f)
    }
    @Test fun waveIsCircularForBothAspectRatiosAndEmptyBoundsAreSafe() {
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(100f, 0f, 500f, 1000f), 0.001f)
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(0f, 100f, 500f, 1000f), 0.001f)
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(100f, 0f, 1000f, 500f), 0.001f)
        assertTrue(OriginalMotionCurves.circleDistance(0f, 0f, 0f, 0f).isFinite())
    }
    @Test fun arrowWaitsForDelayAndLogoHasTwoStages() {
        assertEquals(0f, OriginalMotionCurves.arrowAlpha(1_339f), 0f)
        assertEquals(1f, OriginalMotionCurves.arrowAlpha(1_790f), 0f)
        assertEquals(0.5f, OriginalMotionCurves.logoScale(0f), 0f)
        assertEquals(0.95f, OriginalMotionCurves.logoScale(440f), 0.001f)
        assertEquals(1f, OriginalMotionCurves.logoScale(1_140f), 0.001f)
    }
}
