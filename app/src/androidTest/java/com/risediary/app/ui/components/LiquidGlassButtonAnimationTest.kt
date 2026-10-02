package com.risediary.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Measures rendered content bounds with a controlled spring clock; requires a device. */
class LiquidGlassButtonAnimationTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    private val enabled = mutableStateOf(true)
    private val interactive = mutableStateOf(true)
    private val label = mutableStateOf("检查更新")
    private var clicks = 0

    @Test fun disablingOnClickKeepsTheReboundAndBlocksAnotherClick() {
        mount {
            clicks++
            label.value = "正在检查…"
            enabled.value = false
            interactive.value = false
        }
        val restingWidth = pressAndHold()
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("点击应立即执行一次", 1, clicks)
        compose.onNodeWithTag("button").assertIsNotEnabled()
        assertStillExpanded(restingWidth)
        compose.onNodeWithTag("button").performTouchInput { click() }
        assertEquals("不可点击时不能再次执行操作", 1, clicks)
        assertSettles(restingWidth)
    }

    @Test fun changingOnlyTheLabelKeepsTheRebound() {
        mount { clicks++; label.value = "新状态" }
        val restingWidth = pressAndHold()
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, clicks)
        assertStillExpanded(restingWidth)
        assertSettles(restingWidth)
    }

    @Test fun disablingWhileHeldReleasesTheVisualWithoutInvokingClick() {
        mount { clicks++ }
        val restingWidth = pressAndHold()
        change(enabled, false)
        assertStillExpanded(restingWidth)
        assertSettles(restingWidth)
        compose.onNodeWithTag("button").performTouchInput { up() }
        assertEquals(0, clicks)
    }

    @Test fun turningOffInteractionWhileHeldReleasesAndCanBeEnabledAgain() {
        mount { clicks++ }
        val restingWidth = pressAndHold()
        change(interactive, false)
        assertStillExpanded(restingWidth)
        assertSettles(restingWidth)
        compose.onNodeWithTag("button").performTouchInput { cancel() }
        assertEquals(0, clicks)
        change(interactive, true)
        pressAndHold()
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, clicks)
        assertStillExpanded(restingWidth)
        assertSettles(restingWidth)
    }

    @Test fun rapidDisableAndEnableContinuesFromTheCurrentSpring() {
        mount { clicks++ }
        val restingWidth = pressAndHold()
        change(enabled, false)
        change(enabled, true)
        assertStillExpanded(restingWidth)
        compose.onNodeWithTag("button").performTouchInput { cancel() }
        assertEquals(0, clicks)
        assertSettles(restingWidth)
        compose.onNodeWithTag("button").performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1, clicks)
    }

    private fun mount(onClick: () -> Unit) {
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    LiquidGlassButton(
                        onClick = onClick,
                        backdrop = backdrop,
                        modifier = Modifier.width(220.dp).testTag("button"),
                        enabled = enabled.value,
                        isInteractive = interactive.value,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // A fixed-size child makes scale measurable independently of label length.
                            Box(Modifier.size(32.dp, 4.dp).testTag("marker"))
                            Text(label.value)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun pressAndHold(): Float {
        val resting = markerWidth()
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertStillExpanded(resting)
        return resting
    }

    private fun change(state: MutableState<Boolean>, value: Boolean) {
        compose.runOnIdle { state.value = value }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun markerWidth() =
        compose.onNodeWithTag("marker", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width

    private fun assertStillExpanded(resting: Float) {
        assertTrue("已开始的回弹不能在状态切换时突然归零", markerWidth() > resting + 0.2f)
    }

    private fun assertSettles(resting: Float) {
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals("取消和松手后都应完成回弹", resting, markerWidth(), 0.3f)
    }
}
