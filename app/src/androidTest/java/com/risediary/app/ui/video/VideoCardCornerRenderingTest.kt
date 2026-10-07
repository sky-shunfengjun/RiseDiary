@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.risediary.app.media.Media3VideoPlayerController
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phone rendering regression: a rectangular capture tint must not fill rounded card corners.
 * Tests compile locally; they require a device to verify hardware glass drawing. */
class VideoCardCornerRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lowerCornersStayOutsideTheControlTintInBothThemes() {
        var theme by mutableStateOf("light")
        val pageColor = Color(0xFFE07020)
        compose.setContent {
            val context = LocalContext.current
            val controller = remember { Media3VideoPlayerController(context) }
            val owner = remember(controller) { VideoSurfaceOwner(controller) }
            DisposableEffect(owner) { onDispose { owner.release(); controller.release() } }
            RiseDiaryTheme(themeMode = theme) {
                Box(Modifier.width(320.dp).background(pageColor).padding(8.dp).testTag("page")) {
                    LocalVideoPlayer(controller, owner, false, {},
                        modifier = Modifier.testTag("player"), surfaceEnabled = false)
                }
            }
        }
        listOf("light", "dark").forEach { next ->
            compose.runOnIdle { theme = next }
            compose.waitForIdle()
            val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
            val card = compose.onNodeWithTag("player").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onNodeWithTag("page").captureToImage().toPixelMap()
            val bottom = (card.bottom - page.top).toInt() - 1
            listOf((card.left - page.left).toInt() + 1, (card.right - page.left).toInt() - 2).forEach { x ->
                val pixel = pixels[x, bottom]
                assertEquals("$next red at lower corner", pageColor.red, pixel.red, 0.02f)
                assertEquals("$next green at lower corner", pageColor.green, pixel.green, 0.02f)
                assertEquals("$next blue at lower corner", pageColor.blue, pixel.blue, 0.02f)
            }
        }
    }
}
