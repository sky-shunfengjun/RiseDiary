package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.risediary.app.media.LocalVideoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Exercises the real attachment controls; requires a connected Android device. */
class VideoAttachmentPresentationTest {
    @get:Rule val compose = createComposeRule()

    private val video = LocalVideoRef("content://test/video", "一段用于检查换行与布局的很长视频文件名称.mp4", "video/mp4")

    @Test fun narrowCardWrapsActionsWithoutLosingLargeTextOrTouchTargets() {
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                MiuixTheme {
                    Box(Modifier.width(260.dp).testTag("attachment_container")) {
                        VideoAttachmentCard(video, {}, onSelect = {}, onRemove = {},
                            selectLabel = "重新关联本地视频")
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.8f)) {
            compose.runOnIdle { fontScale.value = scale }
            compose.waitForIdle()
            val container = compose.onNodeWithTag("attachment_container").fetchSemanticsNode().boundsInRoot
            val bounds = listOf("播放", "重新关联本地视频", "移除").map { label ->
                compose.onNode(hasText(label) and hasClickAction())
                    .assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp)
                    .fetchSemanticsNode().boundsInRoot
            }
            bounds.forEach { action ->
                assertTrue("操作不应溢出卡片左侧", action.left >= container.left - 1f)
                assertTrue("操作不应溢出卡片右侧", action.right <= container.right + 1f)
            }
            assertTrue("宽度不足时应换行，保留完整操作入口", bounds.last().top > bounds.first().top + 1f)
        }
    }

    @Test fun busyAttachmentRejectsAllActionsAndAcceptsThemAgainAfterCompletion() {
        val busy = mutableStateOf(true)
        var plays = 0
        var selections = 0
        var removals = 0
        compose.setContent {
            MiuixTheme {
                Box(Modifier.width(360.dp)) {
                    VideoAttachmentCard(video, { plays++ }, onSelect = { selections++ },
                        onRemove = { removals++ }, busy = busy.value)
                }
            }
        }
        listOf("播放", "更换", "移除").forEach { label ->
            compose.onNode(hasText(label) and hasClickAction()).assertIsNotEnabled()
                .performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(0, plays)
            assertEquals(0, selections)
            assertEquals(0, removals)
            busy.value = false
        }
        listOf("播放", "更换", "移除").forEach { label ->
            compose.onNode(hasText(label) and hasClickAction()).performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(1, plays)
            assertEquals(1, selections)
            assertEquals(1, removals)
        }
    }
}
