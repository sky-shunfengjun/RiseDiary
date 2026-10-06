/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.onboarding.original.CircularRevealFrameLayout
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Hardware/UI checks are compiled locally; they require a phone to execute. */
class OnboardingGlassIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun forwardAndReverseNativeSlidesStayOnTheSameHorizontalLine() {
        var root: View? = null
        var frame by mutableStateOf(visualFrame(admission = false, from = OnboardingStep.PROFILE,
            target = OnboardingStep.THEME, progress = 0.5f, transitioning = true, id = 10L))
        compose.setContent { RiseDiaryTheme {
            root = LocalView.current
            Box(Modifier.size(320.dp, 520.dp)) {
                NativeOnboardingHost(
                    ui = OnboardingUiState(step = frame.targetStep, ready = true, loading = false, transitioning = true),
                    frame = frame, active = true, onNext = {}, onBack = {}, onTransitionSettled = { _, _ -> },
                    content = { _, _ -> },
                )
            }
        } }
        fun checkPositions() {
            val scenes = descendants(requireNotNull(root)).filterIsInstance<CircularRevealFrameLayout>()
            assertEquals(2, scenes.size)
            for (scene in scenes) {
                assertEquals("Native scene must never move upwards during a slide", 0f, scene.translationY, 0f)
                assertEquals(0.5f, abs(scene.translationX / scene.width), 0.01f)
            }
        }
        compose.runOnIdle { checkPositions() }
        compose.runOnIdle { frame = visualFrame(admission = false, from = OnboardingStep.THEME,
            target = OnboardingStep.PROFILE, progress = 0.5f, transitioning = true, id = 11L) }
        compose.runOnIdle { checkPositions() }
    }

    @Test fun glassNavigationRespectsConsentAndHasOneActionPerTap() {
        var ui by mutableStateOf(OnboardingUiState(step = OnboardingStep.STATEMENT, ready = true, loading = false))
        var advances = 0; var returns = 0
        compose.setContent { RiseDiaryTheme {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(320.dp, 600.dp)) {
                NativeOnboardingHost(
                    ui, visualFrame(admission = false, target = OnboardingStep.STATEMENT), true,
                    { advances++ }, { returns++ }, { _, _ -> },
                    content = { _, enabled -> StatementOnboardingPage(ui.acceptedStatement, enabled,
                        rememberScrollState(), backdrop, { ui = ui.copy(acceptedStatement = it) }, {}) },
                )
            }
        } }
        compose.onNodeWithTag("oobe_footer").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, advances); ui = ui.copy(acceptedStatement = true) }
        compose.onNodeWithTag("oobe_footer").assertIsEnabled().performClick()
        compose.onNodeWithTag("oobe_back").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, advances); assertEquals(1, returns) }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }
}
