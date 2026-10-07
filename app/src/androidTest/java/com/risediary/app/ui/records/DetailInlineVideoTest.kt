@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.records

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.R
import com.risediary.app.media.*
import com.risediary.app.ui.video.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** These methods compile with the package; executing requires a connected Android device. */
class DetailInlineVideoTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var controller: Media3VideoPlayerController
    private lateinit var session: DetailVideoSession
    private lateinit var root: View
    private lateinit var originalView: PlayerView
    private val fullScreen = mutableStateOf(false)
    private val filename = "关联视频的文件名.mp4"

    @Test fun initialHiddenCardKeepsFilenameAndBlocksPlaybackAndAccessibilityControls() {
        showCard()
        compose.onNodeWithText(filename).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_video_hidden)).assertIsDisplayed()
        assertControlsAbsent()
        compose.runOnIdle {
            assertEquals(Player.STATE_IDLE, controller.player.playbackState)
            assertEquals(0, controller.player.mediaItemCount)
            assertNull(findPlayer(root))
        }
    }

    @Test fun hidingDuringPlaybackPausesAndRevealKeepsPreferences() {
        showCard()
        reveal()
        val bodyBefore = compose.onNodeWithTag("detail_video_card").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle {
            originalView = requireNotNull(findPlayer(root))
            controller.seekTo(500)
            controller.setSpeed(1.5f)
            controller.setLoop(true)
            controller.play()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.detail_video_hide)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_video_hidden)).assertIsDisplayed()
        assertControlsAbsent()
        compose.runOnIdle {
            assertFalse(controller.player.playWhenReady)
            assertNull(findPlayer(root))
        }
        reveal()
        val bodyAfter = compose.onNodeWithTag("detail_video_card").fetchSemanticsNode().boundsInRoot.height
        assertEquals(bodyBefore, bodyAfter, 1f)
        compose.runOnIdle {
            assertSame(originalView, findPlayer(root))
            assertEquals(1.5f, controller.player.playbackParameters.speed, 0f)
            assertEquals(Player.REPEAT_MODE_ONE, controller.player.repeatMode)
            assertTrue(controller.player.currentPosition >= 500)
            assertFalse(controller.player.playWhenReady)
        }
    }

    @Test fun backgroundRehidesButShowingNeverAutoplays() {
        showCard()
        reveal()
        compose.runOnIdle { controller.play(); session.onBackground() }
        compose.onNodeWithText(context.getString(R.string.detail_video_hidden)).assertIsDisplayed()
        assertControlsAbsent()
        reveal()
        compose.runOnIdle { assertFalse(controller.player.playWhenReady) }
    }

    @Test fun inlineFullscreenRoundTripsRetainOneNativeViewAndPausedPlaybackState() {
        showCard()
        reveal()
        compose.runOnIdle {
            originalView = requireNotNull(findPlayer(root))
            controller.seekTo(500)
            controller.setSpeed(2f)
            controller.setLoop(true)
        }
        repeat(6) {
            compose.runOnIdle { fullScreen.value = !fullScreen.value }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(originalView, findPlayer(root))
                assertEquals(1, playerViews(root))
                assertSame(controller.player, originalView.player)
                assertEquals(2f, controller.player.playbackParameters.speed, 0f)
                assertEquals(Player.REPEAT_MODE_ONE, controller.player.repeatMode)
                assertFalse(controller.player.playWhenReady)
            }
        }
    }

    private fun reveal() {
        compose.onNode(hasText(context.getString(R.string.detail_video_show)) and hasClickAction()).performClick()
        compose.waitUntil(15_000) { compose.runOnIdle { controller.player.playbackState == Player.STATE_READY } }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(controller.player.playWhenReady) }
    }

    @Test fun hiddenCardIsCompactAndShowActionIsCentered() {
        showCard()
        compose.waitForIdle()
        val card = compose.onNodeWithTag("detail_video_card").fetchSemanticsNode().boundsInRoot
        val show = compose.onNode(hasText(context.getString(R.string.detail_video_show)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertEquals(card.center.x, show.center.x, 2f)
        reveal()
        val expanded = compose.onNodeWithTag("detail_video_card").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.height > card.height)
        val hide = compose.onNodeWithContentDescription(context.getString(R.string.detail_video_hide)).fetchSemanticsNode().boundsInRoot
        assertTrue(hide.center.x > expanded.center.x)
        assertTrue(hide.bottom < expanded.top + expanded.height / 2)
        compose.onNodeWithText(context.getString(R.string.detail_video_hide)).assertDoesNotExist()
    }

    private fun assertControlsAbsent() {
        for (id in listOf(R.string.video_loop, R.string.video_progress, R.string.video_fullscreen)) {
            compose.onNodeWithContentDescription(context.getString(id)).assertDoesNotExist()
        }
    }

    private fun showCard() {
        context.contentResolver.openFileDescriptor(TestVideoProvider.READABLE, "r")!!.use { }
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = remember { Media3VideoPlayerController(context) }
            session = remember {
                DetailVideoSession(scope, MutableStateFlow(true), { VideoAccessState.READABLE }, controller).also {
                    it.bind(1, LocalVideoRef(TestVideoProvider.READABLE.toString(), filename, "video/mp4"))
                }
            }
            val state by session.state.collectAsState()
            val owner = remember { VideoSurfaceOwner(controller) }
            root = LocalView.current.rootView
            DisposableEffect(owner) { onDispose { owner.release(); controller.release() } }
            MiuixTheme {
                if (fullScreen.value) LocalVideoPlayer(controller, owner, true, { fullScreen.value = false }, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    DetailVideoCard(checkNotNull(state.video), state, controller, owner, session::show, session::hide,
                        session::retry, {}, { fullScreen.value = true }, Modifier.fillMaxWidth().testTag("detail_video_card"))
                }
            }
        }
        compose.waitUntil { compose.runOnIdle { session.state.value.settingsReady } }
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayer(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun playerViews(view: View): Int = if (view is PlayerView) 1 else if (view is ViewGroup)
        (0 until view.childCount).sumOf { playerViews(view.getChildAt(it)) } else 0
}
