@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.risediary.app.media.Media3VideoPlayerController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VideoSurfaceOwnerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateOldHostReleaseDoesNotDetachTheRehostedPlayer() {
        lateinit var context: android.content.Context
        compose.setContent { context = LocalContext.current; Box {} }
        compose.runOnIdle {
            val controller = Media3VideoPlayerController(context)
            val owner = VideoSurfaceOwner(controller)
            try {
                val normal = FrameLayout(context)
                val full = FrameLayout(context)
                val view = owner.mount(normal)
                assertSame(controller.player, view.player)
                assertSame(view, owner.mount(full))
                assertEquals(0, normal.childCount)
                assertEquals(1, full.childCount)
                owner.unmount(normal) // Old AndroidView release arrives after the new factory.
                assertSame(full, view.parent)
                assertSame(controller.player, view.player)
                assertSame(view, owner.mount(normal))
                owner.unmount(full)
                assertSame(normal, view.parent)
                owner.release()
                owner.release()
                assertNull(view.parent)
                assertNull(view.player)
            } finally { owner.release(); controller.release() }
        }
    }
}
