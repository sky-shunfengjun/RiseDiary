package com.risediary.app.ui.library

import com.risediary.app.data.library.*
import com.risediary.app.media.*
import com.risediary.app.service.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryTimerSessionTest {
    private val id = "af461c43-d0b7-4b6a-92a6-100c6a892944"
    private val asset = LibraryAsset(id, null, "video", "a.mp4", "a.mp4", "video/mp4", 100L, 1000L, "a".repeat(64), 0L, 10L, "ready")
    private val snapshot = VideoPlaybackSnapshot(LocalVideoRef("content://com.risediary.app.dev.library/video/$id", "a.mp4", "video/mp4"), 320L, 2f, true)
    private val wall = Clock.fixed(Instant.ofEpochMilli(20_000L), ZoneOffset.UTC)
    private val mono = object : ElapsedRealtimeClock { override fun millis() = 1000L }

    @Test fun preparingDoesNotStartAndClickLocksImmediatelyBeforeAsynchronousValidation() = runTest {
        val holder = TimerStateHolder()
        val timer = Boundary(holder)
        val validation = CompletableDeferred<LibraryAsset?>()
        val model = LibraryTimerSession(backgroundScope, timer, holder, { TimerSession() }, { validation.await() }, LibraryUsageRegistry(), wall, mono)
        model.prepare("s"); runCurrent()
        assertTrue(model.state.value.ready)
        assertTrue(timer.requests.isEmpty())
        model.start(asset, snapshot)
        model.start(asset, snapshot)
        assertTrue(model.state.value.starting)
        assertEquals(id, model.state.value.lockedMaterialId)
        runCurrent()
        assertTrue(timer.requests.isEmpty())
        validation.complete(asset); runCurrent()
        assertEquals(1, timer.requests.size)
        assertEquals(20_000L, timer.requests.single().startedAtEpochMillis)
        assertEquals(1000L, timer.requests.single().startedAtElapsedRealtime)
        assertEquals(snapshot, holder.state.value.video)
        assertEquals(VideoSourceRef.LibraryVideo(id), holder.state.value.videoSource)
        assertTrue(model.state.value.started)
    }

    @Test fun anotherActiveTimerIsPreservedAndCanBeReturnedTo() = runTest {
        val holder = TimerStateHolder().apply { set(TimerSession(status = TimerStatus.PAUSED, sessionId = "other")) }
        val timer = Boundary(holder)
        val model = LibraryTimerSession(backgroundScope, timer, holder, { TimerSession() }, { asset }, LibraryUsageRegistry(), wall, mono)
        model.prepare("s"); runCurrent(); model.start(asset, snapshot); runCurrent()
        assertTrue(timer.requests.isEmpty())
        assertEquals("other", holder.state.value.sessionId)
        assertEquals("other", model.state.value.otherTimer?.sessionId)
        assertNull(model.state.value.lockedMaterialId)
    }

    @Test fun restoringTheSameSessionLocksWithoutNeedingReadableMediaOrRestarting() = runTest {
        val stored = TimerSessionPolicy.start(TimerSession(), TimerStartRequest("s", TimerKind.VIDEO, snapshot, 20_000L, 1000L, VideoSourceRef.LibraryVideo(id)), 20_000L, 1000L, 1)
        val holder = TimerStateHolder()
        val timer = Boundary(holder)
        val model = LibraryTimerSession(backgroundScope, timer, holder, { stored }, { error("Offline media must not prevent restoring timing") }, LibraryUsageRegistry(), wall, mono)
        model.prepare("s"); runCurrent()
        assertTrue(model.state.value.started)
        assertEquals(id, model.state.value.lockedMaterialId)
        assertTrue(timer.requests.isEmpty())
        assertEquals(snapshot, holder.state.value.video)
    }

    @Test fun delayedAcknowledgementKeepsOneMaterialLockedAndRetryUsesTheOriginalRequest() = runTest {
        val holder = TimerStateHolder()
        val timer = Boundary(holder).apply { acknowledge = false }
        val model = LibraryTimerSession(backgroundScope, timer, holder, { TimerSession() }, { asset }, LibraryUsageRegistry(), wall, mono)
        model.prepare("s"); runCurrent(); model.start(asset, snapshot); runCurrent()
        advanceTimeBy(6001L); runCurrent()
        assertFalse(model.state.value.starting)
        assertEquals(id, model.state.value.lockedMaterialId)
        assertNotNull(model.state.value.error)
        timer.acknowledge = true
        model.retry(); runCurrent()
        assertTrue(model.state.value.started)
        assertEquals(1, timer.requests.map { it.sessionId }.distinct().size)
        assertEquals(1, timer.requests.distinct().size)
    }

    @Test fun missingAssetNeverStartsATimerAndReleasesTheTemporaryPin() = runTest {
        val usage = LibraryUsageRegistry()
        val holder = TimerStateHolder()
        val timer = Boundary(holder)
        val model = LibraryTimerSession(backgroundScope, timer, holder, { TimerSession() }, { null }, usage, wall, mono)
        model.prepare("s"); runCurrent(); model.start(asset, snapshot); runCurrent()
        assertTrue(timer.requests.isEmpty())
        assertNull(model.state.value.lockedMaterialId)
        assertNotNull(model.state.value.error)
        usage.reserveDeletion(setOf(id)).close()
    }

    private inner class Boundary(private val holder: TimerStateHolder) : TimerController {
        var acknowledge = true
        val requests = mutableListOf<TimerStartRequest>()
        override val state = holder.state
        override fun restore() = Unit
        override fun start(request: TimerStartRequest) {
            requests += request
            if (acknowledge) holder.set(TimerSessionPolicy.start(state.value, request, wall.millis(), mono.millis(), 1))
        }
        override fun pause(sessionId: String) = Unit
        override fun resume(sessionId: String) = Unit
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) = Unit
        override fun confirmFinish(sessionId: String) = Unit
        override fun cancelFinish(sessionId: String) = Unit
        override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) = Unit
        override fun reset(sessionId: String) = Unit
        override fun discard(sessionId: String) = Unit
    }
}
