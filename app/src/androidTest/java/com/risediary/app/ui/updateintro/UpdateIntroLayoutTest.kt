package com.risediary.app.ui.updateintro

import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.onboarding.*
import com.risediary.app.ui.onboarding.original.*
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** These tests need a phone; compiling them does not validate shadows, blur or animation. */
class UpdateIntroLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun narrowLargeTextRecordingPageKeepsMaximumReachableWithoutNotificationControls() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                RiseDiaryTheme {
                    Box(Modifier.size(280.dp, 420.dp)) {
                        RecordingTimerIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(), true, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("oobe_step_icon").assertDoesNotExist()
        compose.onNodeWithTag("oobe_prediction_slider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.settings_live_updates)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.oobe_enable_notifications)).assertDoesNotExist()
    }

    @Test fun separateNotificationPageKeepsItsSwitchAndExplicitPermissionReachable() {
        var edits = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                RiseDiaryTheme {
                    Box(Modifier.size(280.dp, 420.dp)) {
                        NotificationsIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(),
                            rememberLayerBackdrop(), true, false, {}, { edits++ })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.settings_live_updates))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, edits) }
        compose.onNodeWithText(context.getString(R.string.oobe_enable_notifications))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("oobe_prediction_slider").assertDoesNotExist()
    }

    @Test fun outgoingNotificationSwitchAndPermissionActionStopResponding() {
        var actions = 0
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 460.dp)) {
                NotificationsIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(),
                    rememberLayerBackdrop(), false, false, { actions++ }, { actions++ })
            }
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.settings_live_updates))
            .performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.oobe_enable_notifications))
            .performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test fun outgoingPredictionCannotBeAdjusted() {
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 460.dp)) {
                RecordingTimerIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(), false, {})
            }
        } }
        compose.onNodeWithTag("oobe_prediction_slider").performScrollTo().assertIsNotEnabled()
    }

    @Test fun illustrationsHaveNoPlaybackTimerOrProgressActions() {
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(320.dp, 460.dp)) {
                VideoPrivacyIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(), true, {})
            }
        } }
        val figure = compose.onNodeWithContentDescription(context.getString(R.string.update_intro_player_demo))
            .fetchSemanticsNode()
        assertNull(figure.config.getOrNull(SemanticsActions.OnClick))
        assertNull(figure.config.getOrNull(SemanticsActions.SetProgress))
    }

    @Test fun featureScreenshotsKeepTheirSourceAspectRatioAndHaveNoInputActions() {
        var page by mutableIntStateOf(0)
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(360.dp, 800.dp)) {
                when (page) {
                    0 -> RecordingTimerIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(), true, {})
                    1 -> NotificationsIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(),
                        rememberLayerBackdrop(), true, false, {}, {})
                    else -> VideoPrivacyIntroPage(UpdateIntroUiState(ready = true), rememberScrollState(), true, {})
                }
            }
        } }
        val assets = listOf(R.drawable.update_intro_quantity, R.drawable.update_intro_notifications, R.drawable.update_intro_video)
        assets.forEachIndexed { index, resource ->
            compose.runOnIdle { page = index }
            compose.waitForIdle()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resource, options)
            val image = compose.onNodeWithTag("update_intro_preview", useUnmergedTree = true).fetchSemanticsNode()
            val bounds = image.boundsInRoot
            assertEquals(options.outWidth.toFloat() / options.outHeight,
                bounds.width / bounds.height, 0.01f)
            assertNull(image.config.getOrNull(SemanticsActions.OnClick))
            assertNull(image.config.getOrNull(SemanticsActions.SetProgress))
        }
    }

    @Test fun normalPhoneFeaturePagesFitWithoutScrollingAndPreviewIsAboveTitle() {
        lateinit var scroll: ScrollState
        var page by mutableIntStateOf(0)
        compose.setContent { RiseDiaryTheme {
            scroll = rememberScrollState()
            CompositionLocalProvider(LocalGuideContentPadding provides PaddingValues(top = 88.dp, bottom = 126.dp)) {
                Box(Modifier.size(360.dp, 800.dp)) {
                    when (page) {
                        0 -> RecordingTimerIntroPage(UpdateIntroUiState(ready = true), scroll, true, {})
                        1 -> NotificationsIntroPage(UpdateIntroUiState(ready = true), scroll,
                            rememberLayerBackdrop(), true, false, {}, {})
                        else -> VideoPrivacyIntroPage(UpdateIntroUiState(ready = true), scroll, true, {})
                    }
                }
            }
        } }
        repeat(3) {
            compose.runOnIdle { page = it }
            compose.waitForIdle()
            val preview = compose.onNodeWithTag("update_intro_preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithTag("oobe_step_title").fetchSemanticsNode().boundsInRoot
            assertTrue(preview.bottom <= title.top)
            compose.runOnIdle { assertEquals(0, scroll.maxValue) }
        }
    }

    @Test fun normalStatementCanReadBothPoliciesAndConfirmWithoutScrolling() {
        lateinit var scroll: ScrollState
        compose.setContent { RiseDiaryTheme {
            scroll = rememberScrollState()
            CompositionLocalProvider(LocalGuideContentPadding provides PaddingValues(top = 88.dp, bottom = 126.dp)) {
                Box(Modifier.size(360.dp, 800.dp)) {
                    StatementOnboardingPage(false, true, scroll, rememberLayerBackdrop(), {}, {}, compact = true)
                }
            }
        } }
        compose.onNodeWithText(context.getString(R.string.policy_terms_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.policy_privacy_title)).assertIsDisplayed()
        compose.onNodeWithTag("oobe_agreement").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, scroll.maxValue) }
    }

    @Test fun darkLargeTextVideoPageCanReachAndToggleThePrivacySetting() {
        var choice by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                RiseDiaryTheme(themeMode = "dark") {
                    Box(Modifier.size(280.dp, 420.dp)) {
                        VideoPrivacyIntroPage(UpdateIntroUiState(ready = true, detailVideoHidden = choice),
                            rememberScrollState(), true, { choice = it })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.settings_detail_video_hidden))
            .performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(choice) }
    }

    @Test fun finalPageContainsOnlyItsWelcomeTextWithoutALogo() {
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.size(280.dp, 360.dp)) {
                GuideHero(null, context.getString(R.string.update_intro_complete), null, rememberScrollState())
            }
        } }
        compose.onNodeWithTag("oobe_hero_icon").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.update_intro_complete)).assertIsDisplayed()
        compose.onNodeWithText("v2.0.0").assertDoesNotExist()
    }

    @Test fun immersiveSceneUsesTheWholeViewportAndFooterRemainsOverTheBody() {
        lateinit var root: View
        compose.setContent { RiseDiaryTheme {
            root = LocalView.current.rootView
            Box(Modifier.size(320.dp, 600.dp)) {
                NativeGuideHost(GuideSceneUiState(canContinue = true),
                    GuideVisualFrame(1790f, false, null, 2, 0, 1f, false), true, {}, {}, { _, _ -> },
                    content = { _, enabled -> RecordingTimerIntroPage(UpdateIntroUiState(ready = true),
                        rememberScrollState(), enabled, {}) },
                    spec = GuideSceneSpec(completePage = 5, immersiveBody = true), topBlurProgress = { 1f })
            }
        } }
        compose.onNodeWithTag("update_intro_immersive_scene").assertIsDisplayed()
        compose.onNodeWithTag("update_intro_bottom_fade").assertIsDisplayed()
        compose.onNodeWithTag("oobe_footer").assertIsDisplayed()
        compose.runOnIdle {
            val slot = descendants(root).filterIsInstance<CircularRevealFrameLayout>().single()
            val footer = descendants(slot).first { view ->
                (view.layoutParams as? FrameLayout.LayoutParams)?.gravity == android.view.Gravity.BOTTOM
            }
            assertSame(slot, footer.parent)
            val body = slot.getChildAt(0)
            assertEquals(0, body.top)
            assertEquals(slot.height, body.height)
            assertEquals(0, body.paddingTop)
            assertEquals(0, body.paddingBottom)
        }
    }

    @Test fun updateCheckUsesOneNativeBackgroundWithHorizontalMotionAndCorrectCompletionPage() {
        lateinit var root: View
        var frame by mutableStateOf(GuideVisualFrame(1790f, false, null, 0, 0, 1f, false))
        compose.setContent { RiseDiaryTheme {
            root = LocalView.current.rootView
            Box(Modifier.size(320.dp, 520.dp)) {
                NativeGuideHost(GuideSceneUiState(canContinue = true), frame, true, {}, {}, { _, _ -> },
                    content = { _, _ -> }, spec = GuideSceneSpec(5, GuideWelcomeGraphic.UPDATE_CHECK,
                        R.string.update_intro_success, R.string.update_intro_version, R.string.update_intro_agree, true))
            }
        } }
        compose.runOnIdle {
            assertFalse(root.findViewById<View>(R.id.oobe_original_logo) is ImageView)
            assertEquals(1, descendants(root).filterIsInstance<OriginalIntroBackground>().size)
            frame = GuideVisualFrame(1790f, false, 2, 3, 9, 0.4f, true)
        }
        compose.runOnIdle {
            val bodies = descendants(root).filterIsInstance<CircularRevealFrameLayout>()
            assertEquals(2, bodies.size)
            assertTrue(bodies.all { it.translationY == 0f })
            assertTrue(bodies.all { it.translationX != 0f })
            assertEquals(1, descendants(root).filterIsInstance<OriginalIntroBackground>().size)
            frame = GuideVisualFrame(1790f, false, null, 5, 10, 1f, false)
        }
        compose.runOnIdle {
            assertEquals(View.VISIBLE, descendants(root).filterIsInstance<OriginalIntroBackground>().single().visibility)
        }
    }
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }
}
