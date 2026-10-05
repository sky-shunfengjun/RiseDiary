@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.R
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.Media3VideoPlayerController
import com.risediary.app.media.TestVideoProvider
import com.risediary.app.media.VideoPlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Hidden controls must stop accepting actions at the start of their visual fade. */
class VideoControlsVisibilityTest {
    @get:Rule
    val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    private lateinit var controller: Media3VideoPlayerController
    private lateinit var root: View
    private lateinit var surface: PlayerView
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var exits = 0

    @Test fun hiddenControlsLoseAccessibilityActionsBeforeFadeFinishes() {
        showPlayer()
        val exit = context.getString(R.string.video_exit_fullscreen)
        val loop = context.getString(R.string.video_loop)
        compose.onNodeWithContentDescription(exit).assertIsDisplayed()
        compose.onNodeWithContentDescription(loop).assertIsDisplayed()

        compose.runOnIdle { surface.performClick() }
        compose.mainClock.advanceTimeByFrame()

        // One frame is shorter than the visual fade; outgoing controls are already inaccessible.
        compose.onNodeWithContentDescription(exit).assertDoesNotExist()
        compose.onNodeWithContentDescription(loop).assertDoesNotExist()
        compose.runOnIdle {
            assertSame(surface, findPlayer(root))
            assertSame(controller.player, surface.player)
        }
    }

    @Test fun tappingAnOutgoingExitButtonDoesNotLeaveFullscreen() {
        showPlayer()
        val exitBounds = compose.onNodeWithContentDescription(context.getString(R.string.video_exit_fullscreen))
            .fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { surface.performClick() }
        compose.mainClock.advanceTimeByFrame()

        compose.onRoot().performTouchInput { click(exitBounds.center - rootBounds.topLeft) }
        compose.runOnIdle {
            assertEquals("The fading exit control must not dispatch a command", 0, exits)
            assertSame(surface, findPlayer(root))
        }
        compose.mainClock.advanceTimeBy(500)
    }

    private fun showPlayer() {
        // Generated two-second fixture only; no user media or device permissions are touched.
        context.contentResolver.openFileDescriptor(TestVideoProvider.READABLE, "r")!!.use { }
        compose.setContent {
            controller = remember {
                Media3VideoPlayerController(context).apply {
                    load(VideoPlaybackSnapshot(LocalVideoRef(TestVideoProvider.READABLE.toString(),
                        "test.mp4", "video/mp4")))
                }
            }
            val owner = remember(controller) { VideoSurfaceOwner(controller) }
            DisposableEffect(owner) {
                onDispose { owner.release(); controller.release() }
            }
            root = LocalView.current.rootView
            MiuixTheme {
                LocalVideoPlayer(controller, owner, fullScreen = true,
                    onFullScreen = { exits++ }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.waitUntil(15_000) {
            compose.runOnIdle { controller.player.playbackState == Player.STATE_READY }
        }
        compose.waitForIdle()
        compose.runOnIdle { surface = requireNotNull(findPlayer(root)) }
        compose.mainClock.autoAdvance = false
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPlayer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
