package com.risediary.app.ui.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
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
