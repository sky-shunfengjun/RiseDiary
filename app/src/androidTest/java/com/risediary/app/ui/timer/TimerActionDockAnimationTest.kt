package com.risediary.app.ui.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Run on a device: timer status changes must retain the visible buttons' animation owners. */
class TimerActionDockAnimationTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun startPauseAndResumeKeepTheirReboundAndFinishOpensTheForm() {
        val status = mutableStateOf(TimerStatus.IDLE)
        val actions = mutableListOf<String>()
        fun transition(action: String, next: TimerStatus) {
            actions += action
            status.value = next
        }
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    TimerActionDock(
                        session = TimerSession(status = status.value),
                        backdrop = backdrop,
                        onStart = { transition("start", TimerStatus.RUNNING) },
                        onPause = { transition("pause", TimerStatus.PAUSED) },
                        onResume = { transition("resume", TimerStatus.RUNNING) },
                        onFinish = { transition("finish", TimerStatus.FINISHED) },
                        onRetry = { actions += "retry" },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        clickAndCheckRebound(hasText("开始计时"), hasText("暂停"))
        clickAndCheckRebound(hasText("暂停"), hasText("继续"))
        clickAndCheckRebound(hasText("继续"), hasText("暂停"))
        compose.onNode(hasText("我已起飞")).assertDoesNotExist()
        clickAndCheckRebound(hasText("暂停"), hasText("继续"))
        compose.onNode(hasText("我已起飞")).performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNode(hasText("正在打开…")).assertExists()
        compose.onNode(hasText("填写记录")).assertDoesNotExist()
        compose.onNode(hasText("重新计时")).assertDoesNotExist()
        assertEquals(listOf("start", "pause", "resume", "pause", "finish"), actions)
    }

    @Test fun addingThePausedActionsChangesTheDockHeightGraduallyOnANarrowScreen() {
        val status = mutableStateOf(TimerStatus.RUNNING)
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    TimerActionDock(
                        TimerSession(status = status.value), backdrop, {}, {}, {}, {}, {},
                        modifier = Modifier.width(250.dp).testTag("timer_dock_under_test")
                    )
                }
            }
        }
        compose.waitForIdle()
        val runningHeight = compose.onNodeWithTag("timer_dock_under_test").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { status.value = TimerStatus.PAUSED }
        compose.mainClock.advanceTimeBy(80)
        val intermediateHeight = compose.onNodeWithTag("timer_dock_under_test").fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(1_200)
        val pausedHeight = compose.onNodeWithTag("timer_dock_under_test").fetchSemanticsNode().boundsInRoot.height
        assertTrue("暂停后的双按钮需要更多高度", pausedHeight > runningHeight)
        assertTrue("布局应经过中间高度，不能瞬间跳变", intermediateHeight > runningHeight + 1f && intermediateHeight < pausedHeight - 1f)
    }

    @Test fun anExitingFinishActionCannotSubmitAfterResume() {
        val status = mutableStateOf(TimerStatus.PAUSED)
        var finishes = 0
        var resumes = 0
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    TimerActionDock(
                        TimerSession(status = status.value), backdrop, {}, {},
                        { resumes++; status.value = TimerStatus.RUNNING }, { finishes++ }, {}
                    )
                }
            }
        }
        compose.waitForIdle()
        val previousFinishCenter = compose.onNode(hasText("我已起飞")).fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("继续")).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodes(hasText("我已起飞") and hasClickAction() and isEnabled()).assertCountEquals(0)
        compose.onRoot().performTouchInput { click(previousFinishCenter) }
        compose.runOnIdle {
            assertEquals(1, resumes)
            assertEquals("退场期间的结束按钮不能再次提交", 0, finishes)
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNode(hasText("我已起飞")).assertDoesNotExist()
    }

    private fun clickAndCheckRebound(before: SemanticsMatcher, after: SemanticsMatcher) {
        compose.onNode(before, useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        compose.onNode(before, useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        val duringRebound = compose.onNode(after, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.advanceTimeBy(1_200)
        val atRest = compose.onNode(after, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertTrue("切换状态后的按钮应保留尚未完成的回弹", duringRebound > atRest + 0.2f)
    }
}
