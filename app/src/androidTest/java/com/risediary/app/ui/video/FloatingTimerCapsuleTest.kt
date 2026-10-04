package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.timer.TimerActionDock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class FloatingTimerCapsuleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingExpandsSharedActionsAndBlankTapCloses() {
        val expanded = mutableStateOf(false)
        val position = mutableStateOf(FloatingTimerPosition())
        val status = mutableStateOf(TimerStatus.RUNNING)
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    FloatingTimerCapsule(TimerSession(status = status.value), backdrop, position.value,
                        { position.value = it }, expanded.value, { expanded.value = it },
                        null, null, false, {}) {
                        TimerActionDock(TimerSession(status = status.value), backdrop, {},
                            { status.value = TimerStatus.PAUSED }, { status.value = TimerStatus.RUNNING },
                            {}, {}, fullScreen = true, compact = true, iconOnly = true, enabled = expanded.value)
                    }
                }
            }
        }
        val initial = compose.onNodeWithTag("floating_timer_capsule").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(initial.left < viewport.center.x && initial.top < viewport.center.y)
        compose.onNodeWithTag("floating_timer_capsule").performClick()
        compose.onNodeWithContentDescription("暂停").performClick()
        val resume = compose.onNodeWithContentDescription("继续").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val finish = compose.onNodeWithContentDescription("我已起飞").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(resume.width, resume.height, 1f)
        assertEquals(finish.width, finish.height, 1f)
        assertTrue("Round actions stay on one compact row", kotlin.math.abs(resume.center.y - finish.center.y) < 1f)
        compose.onNodeWithText("暂停").assertDoesNotExist()
        compose.onNodeWithText("继续").assertDoesNotExist()
        compose.onNodeWithText("我已起飞").assertDoesNotExist()
        compose.onNodeWithTag("timer_panel_dismiss").performTouchInput { click(Offset(5f, 5f)) }
        compose.onNodeWithTag("floating_timer_actions").assertDoesNotExist()
        compose.onNodeWithTag("floating_timer_capsule").assertExists()
    }

    @Test fun draggingClosesActionsWithoutTogglingClickAndRemainsInsideViewport() {
        val expanded = mutableStateOf(true)
        val position = mutableStateOf(FloatingTimerPosition(0f, 1f))
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    FloatingTimerCapsule(TimerSession(status = TimerStatus.RUNNING), backdrop,
                        position.value, { position.value = it }, expanded.value,
                        { expanded.value = it }, null, null, false, {}) {}
                }
            }
        }
        val before = compose.onNodeWithTag("floating_timer_capsule").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("floating_timer_capsule").performTouchInput {
            swipe(center, center + Offset(100f, -80f), 500)
        }
        compose.runOnIdle {
            assertFalse(expanded.value)
            assertTrue(position.value.x > 0f)
            assertTrue(position.value.y < 1f)
            assertTrue(position.value.x in 0f..1f && position.value.y in 0f..1f)
        }
        val capsule = compose.onNodeWithTag("floating_timer_capsule").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Continuous input must accumulate instead of losing intermediate deltas", capsule.left - before.left >= 40f)
        assertTrue(capsule.top - before.top <= -30f)
        assertTrue(capsule.left >= root.left && capsule.top >= root.top)
        assertTrue(capsule.right <= root.right && capsule.bottom <= root.bottom)
    }
}
