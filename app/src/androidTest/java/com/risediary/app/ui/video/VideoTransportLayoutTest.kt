@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.media.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class VideoTransportLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsUseAvailableWidthAndKeepIconLoopOnPlaybackRow() {
        lateinit var controller: Media3VideoPlayerController
        val fullscreen = mutableStateOf(false)
        val rowWidth = mutableStateOf(284.dp)
        val loop = mutableStateOf(false)
        compose.setContent {
            val context = LocalContext.current
            controller = remember { Media3VideoPlayerController(context) }
            DisposableEffect(controller) { onDispose { controller.release() } }
            MiuixTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    VideoTransportControls(controller,
                        VideoPlaybackSnapshot(LocalVideoRef("content://test/video", "test.mp4", "video/mp4"), loop = loop.value),
                        60_000L, false, false, backdrop, fullscreen.value, {},
                        Modifier.width(rowWidth.value).testTag("video_transport_test"))
                }
            }
        }
        for (width in listOf(284.dp, 320.dp)) for (mode in listOf(false, true)) {
            compose.runOnIdle { fullscreen.value = mode; rowWidth.value = width }
            compose.waitForIdle()
            val back = "快退 ${controller.player.seekBackIncrement / 1_000L} 秒"
            val forward = "快进 ${controller.player.seekForwardIncrement / 1_000L} 秒"
            val speedBounds = compose.onNodeWithText("1×").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val loopNode = compose.onNodeWithContentDescription("单个视频循环").assertIsDisplayed().assertIsNotSelected()
            for (node in listOf(compose.onNodeWithContentDescription(back),
                compose.onNodeWithContentDescription("播放视频"), compose.onNodeWithContentDescription(forward), loopNode)) {
                assertEquals(speedBounds.center.y, node.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y, 1f)
            }
            val rowBounds = compose.onNodeWithTag("video_transport_test").fetchSemanticsNode().boundsInRoot
            val span = loopNode.fetchSemanticsNode().boundsInRoot.right - speedBounds.left
            assertTrue("Buttons must spread across the available row", span > rowBounds.width * 0.9f)
            compose.onNodeWithText("循环关").assertDoesNotExist()
            compose.onNodeWithText("循环开").assertDoesNotExist()
        }
        compose.runOnIdle { loop.value = true }
        compose.onNodeWithContentDescription("单个视频循环").assertIsSelected()
    }
}
