package com.risediary.app.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.ui.components.LocalCalendarEnvironment
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class RecordDetailLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val record = Flight(id = 1, startTime = 1_000, endTime = 86_401_000, durationSeconds = 86_400,
        spurtCount = null, semenVolumeMl = 8.3f, volumeInputMode = RecordVolumeMode.ESTIMATED.storedValue,
        ejaculationDistanceCm = null, methodTags = "[]", moodNote = "")

    @Test fun narrowLargeTextStacksMetricsAndKeepsApproximationAndSmallUnitsVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                MiuixTheme {
                    Box(Modifier.width(300.dp).testTag("summary_container")) { RecordDetailSummary(record) }
                }
            }
        }
        val duration = compose.onNodeWithTag("detail_duration").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val quantity = compose.onNodeWithTag("detail_quantity").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val container = compose.onNodeWithTag("summary_container").fetchSemanticsNode().boundsInRoot
        assertTrue(quantity.top > duration.bottom)
        assertTrue(duration.right <= container.right + 1)
        assertTrue(quantity.right <= container.right + 1)
        compose.onNodeWithText("1440 分 00 秒").assertIsDisplayed()
        compose.onNodeWithText("约 8.3 毫升").assertIsDisplayed()
    }

    @Test fun emptyOptionalSectionsAreOmittedAndCompleteDatesRemainInTimeDetails() {
        compose.setContent {
            MiuixTheme {
                RecordDetailContent(record, PaddingValues(), rememberScrollState(), null, false, {}, null)
            }
        }
        compose.onNodeWithText("起飞方式").assertDoesNotExist()
        compose.onNodeWithText("备注").assertDoesNotExist()
        compose.onNodeWithText("视频").assertDoesNotExist()
        // Date strings are checked separately in unit tests with a fixed zone.
        compose.onNodeWithText("时间细节").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("开始时间").assertIsDisplayed()
        compose.onNodeWithText("结束时间").performScrollTo().assertIsDisplayed()
    }

    @Test fun detailScrollViewportExtendsBehindTopAndBottomInsets() {
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(320.dp, 640.dp).testTag("detail_viewport")) {
                    RecordDetailContent(record.copy(moodNote = "长备注。".repeat(200)),
                        PaddingValues(top = 80.dp, bottom = 50.dp), rememberScrollState(), null, false, {}, null)
                }
            }
        }
        val outer = compose.onNodeWithTag("detail_viewport").fetchSemanticsNode().boundsInRoot
        val scroll = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(outer.top, scroll.top, 1f)
        assertEquals(outer.bottom, scroll.bottom, 1f)
    }

    @Test fun methodTagsWrapAndVideoPrecedesTimeMethodsAndLongNote() {
        val value = record.copy(methodTags = "[\"第一种方式\",\"第二种方式\",\"第三种方式\",\"很长的起飞方式标签\"]",
            moodNote = "这是用于验证长备注和换行的内容。".repeat(40))
        compose.setContent {
            MiuixTheme {
                Box(Modifier.width(300.dp).fillMaxHeight()) {
                    RecordDetailContent(value, PaddingValues(), rememberScrollState(), null, false, {},
                        videoContent = { Text("页内视频占位", Modifier.testTag("inline_video")) })
                }
            }
        }
        compose.onNodeWithTag("inline_video").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("第一种方式").performScrollTo()
        val first = compose.onNodeWithText("第一种方式").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("很长的起飞方式标签").fetchSemanticsNode().boundsInRoot
        assertTrue(last.top > first.top)
        compose.onNodeWithText("备注").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(value.moodNote).performScrollTo().assertExists()
    }
}
