@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.media

import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerSessionStore
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.video.VideoPlaybackLifecycleObserver
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVideoPlaybackTest {
    private fun context(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = File(base.cacheDir, "video-test-" + UUID.randomUUID())
        return object : ContextWrapper(base) { override fun getFilesDir(): File = files }
    }

    @Test fun loadIsSilentExplicitPlayStartsAndBackgroundRequiresAnotherTap() = runBlocking {
        val context = context()
        context.contentResolver.openFileDescriptor(TestVideoProvider.READABLE, "r")!!.use { }
        val timer = TimerSessionStore(context, com.risediary.app.service.BootIdentityProvider { 1 },
            object : com.risediary.app.service.ElapsedRealtimeClock { override fun millis() = 50_000L },
            java.time.Clock.fixed(java.time.Instant.ofEpochMilli(200_000), java.time.ZoneOffset.UTC))
        val before = TimerSession(status = TimerStatus.PAUSED, elapsedMillis = 12_000,
            startedAtEpochMillis = 100_000, bootCount = 1)
        timer.save(before)
        var controller: Media3VideoPlayerController? = null
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        try {
            withContext(Dispatchers.Main) {
                texture = SurfaceTexture(0)
                surface = Surface(texture)
                controller = Media3VideoPlayerController(context)
                controller!!.player.setVideoSurface(surface)
                controller!!.load(VideoPlaybackSnapshot(LocalVideoRef(TestVideoProvider.READABLE.toString(), "black.mp4", "video/mp4")))
            }
            delay(500)
            assertFalse(controller!!.isPlaying.value)
            withContext(Dispatchers.Main) { controller!!.play() }
            awaitPlaying(controller!!, true)
            withContext(Dispatchers.Main) {
                val owner = object : LifecycleOwner {
                    val registry = LifecycleRegistry(this)
                    override val lifecycle: Lifecycle = registry
                }
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                owner.registry.addObserver(VideoPlaybackLifecycleObserver(controller!!))
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
            awaitPlaying(controller!!, false)
            delay(200)
            assertFalse(controller!!.isPlaying.value)
            withContext(Dispatchers.Main) { controller!!.play() }
            awaitPlaying(controller!!, true)
            withContext(Dispatchers.Main) { controller!!.release() }
            assertFalse(controller!!.isPlaying.value)
            assertEquals(before, timer.load())
        } finally {
            withContext(Dispatchers.Main) {
                controller?.release()
                surface?.release()
                texture?.release()
            }
        }
    }

    @Test fun loopingContinuesBeyondEndAndRestoringSnapshotStaysPaused() = runBlocking {
        val context = context()
        var controller: Media3VideoPlayerController? = null
        try {
            withContext(Dispatchers.Main) {
                controller = Media3VideoPlayerController(context)
                controller!!.load(VideoPlaybackSnapshot(LocalVideoRef(TestVideoProvider.READABLE.toString(), "black.mp4", "video/mp4")))
                controller!!.setLoop(true)
                controller!!.setSpeed(2f)
                controller!!.seekTo(1_700)
                controller!!.play()
            }
            awaitPlaying(controller!!, true)
            delay(1_400)
            assertTrue(controller!!.isPlaying.value)
            val snapshot = withContext(Dispatchers.Main) {
                controller!!.pause()
                requireNotNull(controller!!.playback.value)
            }
            withContext(Dispatchers.Main) { controller!!.load(snapshot) }
            delay(300)
            assertFalse(controller!!.isPlaying.value)
        } finally { withContext(Dispatchers.Main) { controller?.release() } }
    }

    @Test fun fileAccessDistinguishesReadableMissingAndPermissionDenied() = runBlocking {
        val access = AndroidVideoFileAccess(context())
        fun ref(uri: android.net.Uri) = LocalVideoRef(uri.toString(), "black.mp4", "video/mp4")
        assertEquals(VideoAccessState.READABLE, access.check(ref(TestVideoProvider.READABLE)))
        assertEquals(VideoAccessState.MISSING, access.check(ref(TestVideoProvider.MISSING)))
        assertEquals(VideoAccessState.PERMISSION_LOST, access.check(ref(TestVideoProvider.DENIED)))
        assertTrue(access.acquire(TestVideoProvider.READABLE.toString(), 0).isFailure)
    }

    @Test fun secondPreparedPageStopsFirstAndReturnPreservesSettingsWithoutAutoplay() = runBlocking {
        val context = context()
        val coordinator = VideoResourceCoordinator()
        val diagnostics = VideoDiagnostics()
        var first: Media3VideoPlayerController? = null
        var second: Media3VideoPlayerController? = null
        try {
            withContext(Dispatchers.Main) {
                first = Media3VideoPlayerController(context, coordinator, diagnostics)
                second = Media3VideoPlayerController(context, coordinator, diagnostics)
                val ref = LocalVideoRef(TestVideoProvider.READABLE.toString(), "black.mp4", "video/mp4")
                first!!.load(VideoPlaybackSnapshot(ref, 321, 1.5f, true))
                second!!.load(VideoPlaybackSnapshot(ref))
                assertEquals(androidx.media3.common.Player.STATE_IDLE, first!!.player.playbackState)
                assertEquals(VideoPlaybackSnapshot(ref, 321, 1.5f, true), first!!.playback.value)
                first!!.setPresentationActive(true)
                assertEquals(androidx.media3.common.Player.STATE_IDLE, second!!.player.playbackState)
                assertFalse(first!!.player.playWhenReady)
                assertEquals(1.5f, first!!.playback.value!!.speed)
                assertTrue(first!!.playback.value!!.loop)
                first!!.setPresentationActive(false)
                assertEquals(androidx.media3.common.Player.STATE_IDLE, first!!.player.playbackState)
            }
        } finally { withContext(Dispatchers.Main) { first?.release(); second?.release() } }
    }

    private suspend fun awaitPlaying(controller: Media3VideoPlayerController, expected: Boolean) =
        withTimeout(15_000) { while (controller.isPlaying.value != expected) delay(10) }
}
