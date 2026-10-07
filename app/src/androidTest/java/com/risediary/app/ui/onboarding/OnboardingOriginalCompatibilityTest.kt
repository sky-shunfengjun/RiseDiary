/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import android.graphics.Color
import android.graphics.PointF
import android.content.res.Configuration
import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.original.OriginalIntroBackground
import com.risediary.app.ui.onboarding.original.OriginalWelcomeViews
import com.risediary.app.ui.onboarding.original.createOriginalWelcome
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** API-level/device assertions. They are never counted as executed merely because the APK compiles. */
class OnboardingOriginalCompatibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun android12CreatesAndDrawsTheFallbackWithoutLoadingTheShaderBackend() {
        assumeTrue("Execute this case on Android 12/12L", Build.VERSION.SDK_INT in 31..32)
        assumeFalse("Low-memory devices intentionally choose a static fallback",
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(ActivityManager::class.java)?.isLowRamDevice == true)
        lateinit var background: OriginalIntroBackground
        compose.setContent { RiseDiaryTheme {
            AndroidView(factory = { context -> OriginalIntroBackground(context).also { background = it } },
                modifier = Modifier.size(180.dp, 320.dp))
            DisposableEffect(Unit) { onDispose { background.release() } }
        } }
        compose.runOnIdle {
            background.setActive(true)
            background.bind(visualFrame(1790f), PointF(background.width / 2f, background.height * 0.4f))
        }
        commitWindowFrame(background)
        compose.runOnIdle { assertEquals("This evidence must come from the Android 12 Canvas path", IntroBackend.CANVAS, background.backend) }
        val rendered = captureNativeWindow(background)
        saveDeviceEvidence(rendered, "android12_canvas_admission_complete")
        val center = rendered.getPixel(rendered.width / 2, rendered.height / 2)
        assertTrue("The compatible scene must draw rather than stay on the first black mask",
            maxOf(Color.red(center), Color.green(center), Color.blue(center)) > 50)
        rendered.recycle()
    }

    @Test fun outgoingThemeCannotChangeSettingsThroughTouchOrAccessibility() {
        var changes = 0
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 480.dp)) {
                ThemeOnboardingPage("", rememberScrollState(), { changes++ }, enabled = false)
            }
        } }
        for (tag in listOf("oobe_theme_system", "oobe_theme_light", "oobe_theme_dark")) {
            val option = compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
            option.performTouchInput { click() }
            invokeAccessibilityClickIfPresent(option)
        }
        compose.runOnIdle { assertEquals("Retiring content must not invoke settings callbacks", 0, changes) }
    }

    @Test fun outgoingPredictionRejectsTouchAndAccessibilityAdjustments() {
        var changes = 0
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 480.dp)) {
                RecordingOnboardingPage(80, rememberScrollState(), { changes++ }, enabled = false)
            }
        } }
        val slider = compose.onNodeWithTag("oobe_prediction_slider").performScrollTo().assertIsNotEnabled()
        slider.performTouchInput { swipeLeft() }
        val accessibilityAdjust = slider.fetchSemanticsNode().config.getOrNull(SemanticsActions.SetProgress)?.action
        compose.runOnIdle {
            accessibilityAdjust?.invoke(12f)
            assertEquals("Accessibility must not bypass the retiring slider's enabled state", 0, changes)
        }
    }

    @Test fun largeTextStatementKeepsBothReadersAndConsentReachableAtCompactWidths() {
        var fontScale by mutableFloatStateOf(1f)
        var width by mutableFloatStateOf(320f)
        var theme by mutableStateOf("light")
        var reads = 0; var accepted by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RiseDiaryTheme(themeMode = theme) {
                    Box(Modifier.size(width.dp, 440.dp)) {
                        StatementOnboardingPage(accepted, true, rememberScrollState(), rememberLayerBackdrop(),
                            { accepted = it }, { reads++ })
                    }
                }
            }
        }
        for ((viewportWidth, scale, mode) in listOf(Triple(320f, 1f, "light"), Triple(320f, 2f, "light"),
            Triple(280f, 2f, "dark"), Triple(320f, 2f, "dark"))) {
            compose.runOnIdle { width = viewportWidth; fontScale = scale; theme = mode; accepted = false }
            compose.onNodeWithText("使用协议").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("隐私说明").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("oobe_agreement").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue("Consent must remain reachable at ${viewportWidth}dp/${scale}x", accepted) }
        }
        compose.runOnIdle { assertEquals(8, reads) }
    }

    @Test fun outgoingPrivacyActionsCannotCreateALockOrStartBiometricVerification() {
        var operations = 0
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 480.dp)) {
                PrivacyOnboardingPage(OnboardingUiState(appLockEnabled = true), rememberScrollState(), rememberLayerBackdrop(),
                    enabled = false, biometricAvailable = true, onCreateLock = { operations++ },
                    onManageLock = { operations++ }, onEnableBiometric = { operations++ }, onVideoHidden = { operations++ })
            }
        } }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val actions = listOf(
            compose.onNodeWithText(context.getString(com.risediary.app.R.string.onboarding_manage_app_lock)),
            compose.onNodeWithContentDescription(context.getString(com.risediary.app.R.string.settings_biometric_unlock)),
        )
        for (node in actions) {
            val action = node.performScrollTo().assertIsNotEnabled()
            action.performTouchInput { click() }
            invokeAccessibilityClickIfPresent(action)
        }
        compose.runOnIdle { assertEquals(0, operations) }
    }

    @Test fun readFailureKeepsTheHostRetryAccessibleWithoutEnablingSettings() {
        val failed = OnboardingUiState(step = OnboardingStep.PROFILE, ready = false, loading = false, readError = "读取失败")
        var retries = 0; var edits = 0; var hostInteractive = false
        compose.setContent { RiseDiaryTheme {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(320.dp, 520.dp)) {
                NativeOnboardingHost(ui = failed, frame = visualFrame(target = OnboardingStep.PROFILE), active = true,
                    onNext = {}, onBack = {}, onTransitionSettled = { _, _ -> }, content = { _, interactive ->
                        hostInteractive = interactive
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                ProfileOnboardingPage("机长", interactive && failed.ready, rememberScrollState(), { edits++ })
                            }
                            OnboardingAction("重试", AppIcons.Refresh, backdrop, { retries++ }, enabled = interactive)
                        }
                    })
            }
        } }
        compose.onNodeWithTag("oobe_username").assertIsNotEnabled()
        compose.onNodeWithText("重试").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertTrue("Read failure must disable settings, rather than the entire recovery scene", hostInteractive)
            assertEquals(1, retries)
            assertEquals(0, edits)
        }
    }

    @Test fun nativeWelcomeAtLargeFontKeepsItsOriginalSizesAndScrollableArrowReachable() {
        lateinit var welcome: OriginalWelcomeViews
        var clicks = 0
        var point = Offset.Zero
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(280.dp, 360.dp).testTag("native_large_welcome")) {
                AndroidView(factory = { context ->
                    val configuration = Configuration(context.resources.configuration).apply { fontScale = 2f }
                    createOriginalWelcome(context.createConfigurationContext(configuration), { clicks++ }).also {
                        welcome = it
                        it.next.isEnabled = true; it.next.isClickable = true; it.next.alpha = 1f
                    }.root
                }, modifier = Modifier.fillMaxSize())
            }
        } }
        compose.runOnIdle {
            val density = welcome.root.resources.displayMetrics.density
            assertEquals(90f * density, welcome.logo.width.toFloat(), 1f)
            assertEquals(70f * density, welcome.next.width.toFloat(), 1f)
            assertEquals(70f * density, welcome.next.height.toFloat(), 1f)
            welcome.next.requestRectangleOnScreen(android.graphics.Rect(0, 0, welcome.next.width, welcome.next.height), true)
        }
        compose.runOnIdle {
            val visible = android.graphics.Rect()
            assertTrue("The circular action must be fully reachable after scrolling", welcome.next.getGlobalVisibleRect(visible))
            assertEquals(welcome.next.height, visible.height())
            val origin = IntArray(2); val action = IntArray(2)
            welcome.root.getLocationOnScreen(origin); welcome.next.getLocationOnScreen(action)
            point = Offset((action[0] - origin[0]).toFloat() + welcome.next.width / 2f,
                (action[1] - origin[1]).toFloat() + welcome.next.height / 2f)
        }
        compose.onNodeWithTag("native_large_welcome").performTouchInput { click(point) }
        compose.runOnIdle { assertEquals("Large text must leave one working native next action", 1, clicks) }
    }
}

private fun invokeAccessibilityClickIfPresent(node: SemanticsNodeInteraction) {
    // A disabled action may either be absent or reject invocation. Both must leave the callback untouched.
    if (node.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.action != null) {
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
}
