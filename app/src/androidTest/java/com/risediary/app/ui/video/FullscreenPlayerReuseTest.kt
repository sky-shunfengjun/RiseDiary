@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import com.risediary.app.ui.components.SecondaryPageScaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.media.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Device check: layout switches must retain the same PlayerView and media settings. */
class FullscreenPlayerReuseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedLayoutSwitchesRetainViewPlayerPositionSpeedAndLoop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.contentResolver.openFileDescriptor(TestVideoProvider.READABLE, "r")!!.use { }
        lateinit var controller: Media3VideoPlayerController
        lateinit var root: View
        val fullscreen = mutableStateOf(false)
        try {
            compose.setContent {
                controller = remember {
                    Media3VideoPlayerController(context).apply {
                        load(VideoPlaybackSnapshot(LocalVideoRef(TestVideoProvider.READABLE.toString(), "test.mp4", "video/mp4")))
                    }
                }
                MiuixTheme {
                    root = LocalView.current.rootView
                    val owner = remember(controller) { VideoSurfaceOwner(controller) }
                    DisposableEffect(owner) { onDispose { owner.release() } }
                    val content: @Composable () -> Unit = {
                        LocalVideoPlayer(controller, owner, fullscreen.value, { fullscreen.value = !fullscreen.value },
                            if (fullscreen.value) Modifier.fillMaxSize() else Modifier)
                    }
                    if (fullscreen.value) Box(Modifier.fillMaxSize()) { content() }
                    else SecondaryPageScaffold("Video", {}) { content() }
                }
            }
            compose.waitUntil(15_000) { compose.runOnIdle { controller.player.playbackState == Player.STATE_READY } }
            lateinit var originalView: PlayerView
            var expectedPosition = 0L
            compose.runOnIdle {
                originalView = requireNotNull(findPlayer(root))
                controller.setSpeed(1.5f)
                controller.setLoop(true)
                controller.seekTo(500)
            }
            compose.waitForIdle()
            compose.waitUntil(8_000) { compose.runOnIdle { controller.player.currentPosition == 500L } }
            compose.runOnIdle { expectedPosition = controller.player.currentPosition }
            repeat(8) {
                compose.runOnIdle { fullscreen.value = !fullscreen.value }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertSame(originalView, findPlayer(root))
                    assertSame(controller.player, originalView.player)
                    assertEquals(expectedPosition, controller.player.currentPosition)
                    assertEquals(1.5f, controller.player.playbackParameters.speed, 0f)
                    assertEquals(Player.REPEAT_MODE_ONE, controller.player.repeatMode)
                    assertFalse(controller.player.playWhenReady)
                }
            }
        } finally { compose.runOnIdle { controller.release() } }
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPlayer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
