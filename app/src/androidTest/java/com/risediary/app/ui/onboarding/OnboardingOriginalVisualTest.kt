/*
 * Independent rendering fixture adapted from HyperCeiler GlowPainter / RenderViewLayout.
 * Upstream: ReChronoRain/HyperCeiler, commit 5e4686069dd7ab1f3697e256d5fc7d68fb73e317.
 * Copyright the HyperCeiler contributors; Copyright (C) 2026 sky-shunfengjun.
 * SPDX-License-Identifier: AGPL-3.0-only. See LICENSES/AGPL-3.0.txt.
 */
package com.risediary.app.ui.onboarding

import android.content.Context
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.risediary.app.R
import com.risediary.app.ui.onboarding.original.CircularRevealFrameLayout
import com.risediary.app.ui.onboarding.original.OriginalIntroBackground
import com.risediary.app.ui.theme.RiseDiaryTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** These assertions require real window drawing. Compiling this suite is not device validation. */
class OnboardingOriginalVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completeOriginalShaderDrawsLikeTheUpstreamFixtureAtAdmissionKeyframes() {
        assumeTrue("RuntimeShader is available from Android 13", Build.VERSION.SDK_INT >= 33)
        assumeFalse("Low-memory devices intentionally use a compatible renderer",
            InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true)
        if (Build.VERSION.SDK_INT < 33) return
        lateinit var actual: OriginalIntroBackground
        lateinit var reference: UpstreamShaderScene
        compose.setContent {
            RiseDiaryTheme {
                Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AndroidView(factory = { context -> OriginalIntroBackground(context).also { actual = it } },
                        modifier = Modifier.size(140.dp, 280.dp))
                    AndroidView(factory = { context -> UpstreamShaderScene(context).also { reference = it } },
                        modifier = Modifier.size(140.dp, 280.dp))
                }
                DisposableEffect(Unit) { onDispose { actual.release(); reference.release() } }
            }
        }
        compose.runOnIdle { assertTrue("A software snapshot cannot validate the shader", actual.isHardwareAccelerated) }
        for (millis in listOf(0f, 200f, 440f, 700f, 1140f, 1340f, 1440f, 1790f)) {
            compose.runOnIdle {
                actual.setActive(true)
                actual.bind(visualFrame(millis), PointF(actual.width / 2f, actual.height * 0.4f))
                reference.bind(millis / 1000f, admission = true)
            }
            commitWindowFrame(actual)
            compose.runOnIdle { assertEquals("The real adapter must select its public original-shader path", IntroBackend.ORIGINAL_SHADER, actual.backend) }
            val rendered = captureNativeWindow(actual)
            val baseline = captureNativeWindow(reference)
            saveDeviceEvidence(rendered, "original_shader_${millis.toInt()}")
            saveDeviceEvidence(baseline, "upstream_fixture_${millis.toInt()}")
            assertTrue("The complete original shader/defaults differ at ${millis}ms",
                sampledRgbDifference(rendered, baseline) < 0.055f)
            if (millis == 0f) assertTrue("The original admission starts with an opaque black mask", sampledBrightness(rendered) < 0.02f)
            if (millis == 1790f) assertTrue("A compiled effect must also draw the original coloured background", sampledBrightness(rendered) > 0.25f)
            rendered.recycle()
            baseline.recycle()
        }
    }

    @Test fun admissionEndsWithoutRemovingOrRestartingTheFlowingBackground() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assumeFalse("Low-memory devices intentionally use a static fallback",
            InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true)
        lateinit var background: OriginalIntroBackground
        compose.setContent { RiseDiaryTheme {
            AndroidView(factory = { context -> OriginalIntroBackground(context).also { background = it } },
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).size(180.dp, 320.dp))
            DisposableEffect(Unit) { onDispose { background.release() } }
        } }
        fun render(millis: Float): Bitmap {
            compose.runOnIdle {
                background.setActive(true)
                background.bind(visualFrame(millis, admission = false), PointF(background.width / 2f, background.height * 0.4f))
            }
            commitWindowFrame(background)
            return captureNativeWindow(background)
        }
        val first = render(1800f)
        val later = render(4800f)
        saveDeviceEvidence(first, "post_admission_1800")
        saveDeviceEvidence(later, "post_admission_4800")
        assertTrue("Ending the entrance must not turn the welcome background black", sampledBrightness(later) > 0.25f)
        assertTrue("The full original background should keep moving after admission", sampledRgbDifference(first, later) > 0.01f)
        first.recycle(); later.recycle()
    }

    @Test fun oneCircularClipIncludesTheSceneBackgroundAndTheBottomAction() {
        lateinit var scene: CircularRevealFrameLayout
        lateinit var viewport: FrameLayout
        compose.setContent { RiseDiaryTheme {
            AndroidView(factory = { context ->
                FrameLayout(context).apply {
                    viewport = this
                    setBackgroundColor(Color.GREEN)
                    scene = CircularRevealFrameLayout(context).apply {
                        setBackgroundColor(Color.RED)
                        addView(View(context).apply { setBackgroundColor(Color.BLUE) },
                            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                        addView(View(context).apply { setBackgroundColor(Color.YELLOW) },
                            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).roundToInt(), android.view.Gravity.BOTTOM))
                    }
                    addView(scene, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
            }, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).size(200.dp, 300.dp))
        } }
        lateinit var geometry: NativeRevealGeometry
        compose.runOnIdle {
            geometry = NativeRevealGeometry(scene.width / 2f, scene.height - 24f * scene.resources.displayMetrics.density,
                10f * scene.resources.displayMetrics.density,
                hypot(scene.width / 2f, scene.height - 24f * scene.resources.displayMetrics.density) + 1f)
            scene.bindReveal(geometry, 0f, visibleInside = true)
        }
        commitWindowFrame(viewport)
        val start = captureNativeWindow(viewport)
        assertPixelNear(Color.GREEN, start.getPixel(2, 2), "Background must be clipped with all children")
        assertPixelNear(Color.YELLOW, start.getPixel(geometry.centerX.roundToInt(), geometry.centerY.roundToInt()), "The bottom action belongs to the same reveal")
        compose.runOnIdle { scene.bindReveal(geometry, 1f, visibleInside = true) }
        commitWindowFrame(viewport)
        val complete = captureNativeWindow(viewport)
        assertPixelNear(Color.BLUE, complete.getPixel(2, 2), "Final radius must cover the farthest corner")
        assertPixelNear(Color.YELLOW, complete.getPixel(complete.width / 2, complete.height - 2), "The full bottom action must be present")
        compose.runOnIdle { scene.clearReveal() }
        start.recycle(); complete.recycle()
    }

    @Test fun productionWelcomeRevealKeepsTheNewBodyAndFooterInsideTheSameCircle() {
        var frame by mutableStateOf(visualFrame(1790f, admission = false))
        lateinit var owner: View
        var arrow = Rect.Zero
        compose.setContent { RiseDiaryTheme {
            val currentOwner = LocalView.current
            SideEffect { owner = currentOwner }
            Box(Modifier.size(320.dp, 520.dp).testTag("reveal_evidence_scene")) {
                NativeOnboardingHost(ui = OnboardingUiState(step = frame.targetStep, ready = true, loading = false,
                    acceptedStatement = true, transitioning = frame.transitioning), frame = frame, active = true,
                    onNext = {}, onBack = {}, onTransitionSettled = { _, _ -> },
                    onGeometry = { _, bounds, _ -> arrow = bounds }, content = { step, _ ->
                        if (step == OnboardingStep.STATEMENT) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Magenta))
                    })
            }
        } }
        fun captureScene(): Bitmap {
            commitWindowFrame(owner)
            val bounds = compose.onNodeWithTag("reveal_evidence_scene").fetchSemanticsNode().boundsInRoot
            val window = captureNativeWindow(owner)
            return Bitmap.createBitmap(window, bounds.left.roundToInt(), bounds.top.roundToInt(),
                bounds.width.roundToInt(), bounds.height.roundToInt()).also { if (it !== window) window.recycle() }
        }
        val welcome = captureScene()
        compose.runOnIdle {
            assertTrue(arrow.width > 0f)
            frame = visualFrame(1790f, admission = false, from = OnboardingStep.WELCOME,
                target = OnboardingStep.STATEMENT, progress = 0f, transitioning = true, id = 21L)
        }
        val start = captureScene()
        val x = (start.width * 0.1f).roundToInt()
        val footerY = arrow.center.y.roundToInt().coerceIn(0, start.height - 1)
        assertPixelNear(welcome.getPixel(x, footerY), start.getPixel(x, footerY),
            "A separate, unclipped target footer must not appear outside the initial circle")
        assertPixelNear(welcome.getPixel(start.width / 2, (start.height * 0.6f).roundToInt()),
            start.getPixel(start.width / 2, (start.height * 0.6f).roundToInt()),
            "The target body must be clipped by the same initial circle")
        compose.runOnIdle { frame = frame.copy(transitionProgress = 1f) }
        val end = captureScene()
        assertPixelNear(Color.MAGENTA, end.getPixel(end.width / 2, (end.height * 0.6f).roundToInt()),
            "The final reveal must show the incoming body rather than an intermediate page")
        saveDeviceEvidence(welcome, "host_reveal_welcome")
        saveDeviceEvidence(start, "host_reveal_start")
        saveDeviceEvidence(end, "host_reveal_complete")
        welcome.recycle(); start.recycle(); end.recycle()
    }
}

internal fun visualFrame(millis: Float = 1790f, admission: Boolean = true,
    from: OnboardingStep? = null, target: OnboardingStep = OnboardingStep.WELCOME,
    progress: Float = 1f, transitioning: Boolean = false, id: Long = 0L) =
    OnboardingVisualFrame(millis, admission, from, target, id, progress, transitioning)

/** Wait for a committed hardware frame rather than drawing the View into a software Bitmap. */
internal fun commitWindowFrame(view: View) {
    val commit = CountDownLatch(1)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        view.viewTreeObserver.registerFrameCommitCallback { commit.countDown() }
        view.invalidate()
    }
    assertTrue("No hardware window frame was committed", commit.await(5, TimeUnit.SECONDS))
}

internal fun captureNativeWindow(view: View): Bitmap {
    val location = IntArray(2)
    var width = 0; var height = 0
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        assertTrue("The actual View must be attached to a visible window", view.isAttachedToWindow)
        view.getLocationOnScreen(location)
        width = view.width; height = view.height
    }
    val full = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "Window screenshot unavailable" }
    assertTrue("Evidence viewport must be on screen", width > 0 && height > 0 && location[0] >= 0 && location[1] >= 0 && location[0] + width <= full.width && location[1] + height <= full.height)
    return Bitmap.createBitmap(full, location[0], location[1], width, height).also { if (it !== full) full.recycle() }
}

internal fun saveDeviceEvidence(bitmap: Bitmap, name: String) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val folder = File(requireNotNull(context.getExternalFilesDir(null)), "dev35-onboarding-evidence")
    check(folder.exists() || folder.mkdirs())
    File(folder, "api${Build.VERSION.SDK_INT}_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun sampledRgbDifference(first: Bitmap, second: Bitmap): Float {
    assertEquals(first.width, second.width); assertEquals(first.height, second.height)
    var difference = 0f; var samples = 0
    for (y in 2 until first.height - 2 step max(1, first.height / 24)) {
        for (x in 2 until first.width - 2 step max(1, first.width / 24)) {
            val a = first.getPixel(x, y); val b = second.getPixel(x, y)
            difference += abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
            samples += 3
        }
    }
    return difference / (samples * 255f)
}

private fun sampledBrightness(bitmap: Bitmap): Float {
    var brightness = 0f; var samples = 0
    for (y in 1 until bitmap.height step max(1, bitmap.height / 16)) {
        for (x in 1 until bitmap.width step max(1, bitmap.width / 16)) {
            val color = bitmap.getPixel(x, y)
            brightness += max(Color.red(color), max(Color.green(color), Color.blue(color))) / 255f
            samples++
        }
    }
    return brightness / samples
}

private fun assertPixelNear(expected: Int, actual: Int, message: String) {
    assertTrue(message, abs(Color.red(expected) - Color.red(actual)) <= 4 &&
        abs(Color.green(expected) - Color.green(actual)) <= 4 && abs(Color.blue(expected) - Color.blue(actual)) <= 4)
}

/** Independent literal fixture from upstream GlowPainter, not the production parameter builder. */
@RequiresApi(33)
private class UpstreamShaderScene(context: Context) : FrameLayout(context) {
    private val shader = RuntimeShader(resources.openRawResource(R.raw.oobe_original_glow).bufferedReader().use { it.readText() })
    private val effectView = View(context).apply { setBackgroundColor(Color.BLACK) }

    init {
        mapOf("uScale2" to 0.82f, "uSpeed2" to 0.49f, "uColorInMin" to 0.3f, "uColorInMax" to 1f,
            "uColorOutMin" to 0.3f, "uColorOutMax" to 0.86f, "uColorMidPoint" to 0.47f, "uUseOklab" to 1f,
            "uScale" to 1.3f, "uSpeed" to 0.4f, "uBrightnessInMin" to 0.25f, "uBrightnessInMax" to 1f,
            "uBrightnessOutMin" to 0.25f, "uBrightnessOutMax" to 1f, "uShowCircle" to 1f,
            "uCircleThickness" to 0.4f, "uCircleFinalRadius" to 1f, "uCircleYOffset" to 0.1f,
            "uCircleSpeed" to 0.9f, "uCircleColorFreq" to 1f, "uCircleColorSpeed" to 0f,
            "uCircleEasing" to 1.4f, "uCircleAnimationOffset" to 0f, "uMaskDelay" to 0.3f,
            "uMaskThickness" to 0.3f, "uCircleScreenBlend" to 1f, "uCircleAddBlend" to 0.04f,
            "uCircleColorOffset" to 0.25f, "uCircleUVDistort" to 0f, "uColorToDistortWidthRatio" to 0.6f,
            "uDistortStartTime" to 0.2f, "uDistortEndTime" to 0.3f, "uDistortStart" to 0f,
            "uDistortEnd" to 1f, "uStripeFrequency" to 0f, "uStripeStrengthX" to 0f,
            "uStripeStrengthY" to 0f, "uStripeUVDistort" to 0f).forEach { (name, value) -> shader.setFloatUniform(name, value) }
        shader.setFloatUniform("uColorBlack", 0.961f, 0.157f, 0.157f)
        shader.setFloatUniform("uColorMid", 0.604f, 0.659f, 0.961f)
        shader.setFloatUniform("uColorWhite", 0.302f, 0.29f, 0.843f)
        addView(effectView)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = ceil(width * 0.2f).toInt(); val h = ceil(height * 0.2f).toInt()
        val x = (width - w) / 2; val y = (height - h) / 2
        effectView.scaleX = 5f; effectView.scaleY = 5f
        effectView.layout(x, y, x + w, y + h)
    }

    fun bind(seconds: Float, admission: Boolean) {
        shader.setFloatUniform("uTime", seconds)
        shader.setFloatUniform("uResolution", effectView.width.toFloat(), effectView.height.toFloat())
        shader.setFloatUniform("uShowCircle", if (admission) 1f else 0f)
        effectView.setRenderEffect(RenderEffect.createShaderEffect(shader))
        effectView.invalidate()
    }

    fun release() { effectView.setRenderEffect(null) }
}
