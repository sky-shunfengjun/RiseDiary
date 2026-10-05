package com.risediary.app.ui.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class QuantityModePresentationTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test fun leavingManualInputDisablesTheOutgoingFieldAndKeepsItsValueWhenReturning() {
        val estimated = mutableStateOf(false)
        val manualValue = mutableStateOf("5.2")
        var outgoingManualEnabled = true
        var edits = 0
        compose.setContent {
            MiuixTheme {
                Box(Modifier.width(280.dp)) {
                    QuantityModeContent(estimated.value,
                        estimatedContent = { Text("预测输入", Modifier.testTag("estimate")) },
                        manualContent = { active ->
                            SideEffect { outgoingManualEnabled = active }
                            BasicTextField(value = manualValue.value,
                                onValueChange = { manualValue.value = it; edits++ },
                                enabled = active,
                                modifier = Modifier.testTag("manual"))
                        })
                }
            }
        }
        compose.onNodeWithTag("manual").performClick()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { estimated.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertFalse("退场动画不能保留可编辑的旧分支", outgoingManualEnabled)
        }
        compose.onAllNodes(isFocused()).assertCountEquals(0)
        compose.onAllNodes(isEnabled() and hasSetTextAction()).assertCountEquals(0)
        if (compose.onAllNodesWithTag("manual", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("manual", useUnmergedTree = true).assertIsNotEnabled()
                .performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals("5.2", manualValue.value)
            assertEquals(0, edits)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("estimate").assertIsDisplayed()
        compose.runOnIdle { estimated.value = false }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("manual").assertTextEquals("5.2")
        compose.mainClock.autoAdvance = true
    }

    @Test fun leavingPredictionRemovesProgressActionAndRejectsAnOutgoingSliderDrag() {
        val estimated = mutableStateOf(true)
        val ticks = mutableStateOf(32)
        var outgoingPredictionEnabled = true
        compose.setContent {
            MiuixTheme {
                Box(Modifier.width(280.dp)) {
                    QuantityModeContent(estimated.value,
                        estimatedContent = { active ->
                            SideEffect { outgoingPredictionEnabled = active }
                            PredictionVolumeSlider(ticks.value, 80, { ticks.value = it }, enabled = active)
                        },
                        manualContent = { Text("毫升输入") })
                }
            }
        }
        val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        val bounds = compose.onNode(progress).fetchSemanticsNode().boundsInRoot
        compose.onNode(progress).performSemanticsAction(SemanticsActions.SetProgress) { it(4.2f) }
        compose.runOnIdle { assertEquals(42, ticks.value) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { estimated.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertFalse("退场的预测滑块不能继续响应", outgoingPredictionEnabled)
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(0)
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(Offset(bounds.left - rootBounds.left + bounds.width * 0.15f, bounds.center.y - rootBounds.top),
                Offset(bounds.left - rootBounds.left + bounds.width * 0.9f, bounds.center.y - rootBounds.top))
        }
        compose.runOnIdle { assertEquals("离场拖动不能覆盖已填数量", 42, ticks.value) }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
    }
}
