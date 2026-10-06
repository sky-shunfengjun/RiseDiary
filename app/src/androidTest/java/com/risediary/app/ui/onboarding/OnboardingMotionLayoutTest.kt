/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Dev34 behaviours migrated onto the original native scene; no obsolete renderer remains just for tests. */
class OnboardingMotionLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenNativeWelcomeArrowCannotNavigateBeforeAdmissionEnds() {
        var frame by mutableStateOf(visualFrame(0f))
        var clicks = 0
        var arrow = Rect.Zero
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 520.dp).testTag("native_scene")) {
                NativeOnboardingHost(ui = OnboardingUiState(ready = true, loading = false), frame = frame,
                    active = true, onNext = { clicks++ }, onBack = {}, onTransitionSettled = { _, _ -> },
                    onGeometry = { _, bounds, _ -> arrow = bounds }, content = { _, _ -> })
            }
        } }
        compose.runOnIdle { assertTrue("The arrow must be measured in scene coordinates", arrow.width > 0f && arrow.height > 0f) }
        compose.onNodeWithTag("native_scene").performTouchInput { click(arrow.center) }
        compose.runOnIdle { assertEquals(0, clicks); frame = visualFrame(1790f, admission = false) }
        compose.onNodeWithTag("native_scene").performTouchInput { click(arrow.center) }
        compose.runOnIdle { assertEquals("The same native arrow becomes usable after admission", 1, clicks) }
    }

    @Test fun welcomeRevealBlocksTheOutgoingArrowAndIncomingConsentTogether() {
        var callbacks = 0
        var arrow = Rect.Zero
        val frame = visualFrame(1790f, admission = false, from = OnboardingStep.WELCOME,
            target = OnboardingStep.STATEMENT, progress = 0.6f, transitioning = true, id = 7L)
        compose.setContent { RiseDiaryTheme {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(320.dp, 520.dp).testTag("native_scene")) {
                NativeOnboardingHost(ui = OnboardingUiState(step = OnboardingStep.STATEMENT, ready = true, loading = false, transitioning = true),
                    frame = frame, active = true, onNext = { callbacks++ }, onBack = { callbacks++ },
                    onTransitionSettled = { _, _ -> callbacks++ }, onGeometry = { _, bounds, _ -> arrow = bounds },
                    content = { step, enabled ->
                        if (step == OnboardingStep.STATEMENT) StatementOnboardingPage(false, enabled,
                            rememberScrollState(), backdrop, { callbacks++ }, { callbacks++ })
                    })
            }
        } }
        // The host may remove retiring actions from semantics, or expose explicitly disabled actions.
        val consent = compose.onAllNodesWithTag("oobe_agreement")
        for (index in consent.fetchSemanticsNodes().indices) {
            val action = consent[index].assertIsNotEnabled()
            if (action.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.action != null) {
                action.performSemanticsAction(SemanticsActions.OnClick) { it() }
            }
        }
        val readers = compose.onAllNodesWithText("使用协议")
        for (index in readers.fetchSemanticsNodes().indices) readers[index].assertIsNotEnabled()
        compose.onNodeWithTag("native_scene").performTouchInput { click(arrow.center) }
        compose.runOnIdle { assertEquals("No part of a half-revealed scene may complete or edit", 0, callbacks) }
    }

    @Test fun statementHeroIconAndTitleStayCenteredInTheOriginalCompactBody() {
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(280.dp, 460.dp).testTag("statement_viewport")) {
                StatementOnboardingPage(false, true, rememberScrollState(), rememberLayerBackdrop(), {}, {})
            }
        } }
        val viewport = compose.onNodeWithTag("statement_viewport").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("oobe_step_icon").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("oobe_step_title").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.x, icon.center.x, 1f)
        assertEquals(viewport.center.x, title.center.x, 1f)
    }

    @Test fun inactiveWelcomeCannotNavigateAndResumesWithoutReplayingAdmission() {
        var active by mutableStateOf(false)
        var callbacks = 0
        var arrow = Rect.Zero
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 520.dp).testTag("native_scene")) {
                NativeOnboardingHost(ui = OnboardingUiState(ready = true, loading = false),
                    frame = visualFrame(1790f, admission = false), active = active, onNext = { callbacks++ },
                    onBack = {}, onTransitionSettled = { _, _ -> },
                    onGeometry = { _, bounds, _ -> arrow = bounds }, content = { _, _ -> })
            }
        } }
        compose.onNodeWithTag("native_scene").performTouchInput { click(arrow.center) }
        compose.runOnIdle { assertEquals(0, callbacks); active = true }
        compose.onNodeWithTag("native_scene").performTouchInput { click(arrow.center) }
        compose.runOnIdle { assertEquals("Returning at the retained final frame must not introduce another delay", 1, callbacks) }
    }

    @Test fun disabledSystemAnimationsFinishEveryNewTransitionAfterWelcomeAdmission() {
        val motion = OnboardingMotionState()
        motion.startIntro(Offset(160f, 200f), animationsEnabled = false)
        compose.setContent { OnboardingMotionClock(motion, active = true, durationScale = 0f) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(motion.canContinue)
            motion.observeStep(OnboardingStep.STATEMENT)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse("Zero duration scale must complete a newly created welcome transition", motion.isTransitioning)
            assertTrue(motion.canContinue)
            motion.observeStep(OnboardingStep.PROFILE)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse("Later transition tickets must also complete without an animation frame loop", motion.isTransitioning)
            assertTrue(motion.canContinue)
        }
    }

    @Test fun realClockStopsWhileInactiveAndContinuesTheSameAdmissionOnReturn() {
        compose.mainClock.autoAdvance = false
        val motion = OnboardingMotionState().apply { startIntro(Offset(160f, 200f)) }
        var active by mutableStateOf(true)
        var frozen = 0f
        compose.setContent { OnboardingMotionClock(motion, active, durationScale = 1f) }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertTrue("The foreground clock should have begun admission", motion.introElapsedMillis > 0f)
            assertTrue(motion.introRunning)
            active = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { frozen = motion.introElapsedMillis }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {
            assertEquals("Inactive time must not advance the first-open lightwave", frozen, motion.introElapsedMillis, 0f)
            active = true
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue("Returning should continue, rather than reset, the same admission", motion.introElapsedMillis > frozen)
            assertTrue(motion.introRunning)
        }
    }
}
