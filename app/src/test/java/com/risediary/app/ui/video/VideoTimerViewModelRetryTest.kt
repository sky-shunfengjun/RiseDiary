package com.risediary.app.ui.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.media.*
import com.risediary.app.service.*
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoTimerViewModelRetryTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val video = LocalVideoRef("content://videos/one", "one.mp4", "video/mp4")
    private val snapshot = VideoPlaybackSnapshot(video, positionMillis = 340L, speed = 1.5f, loop = true)
    private val wall = Clock.fixed(Instant.ofEpochMilli(20_000), ZoneOffset.UTC)
    private val elapsed = object : ElapsedRealtimeClock { override fun millis() = 5_000L }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun failedInitialReadRetryReallyReadsPreparesAndAllowsFirstPlaybackToStartTimer() = runTest(dispatcher) {
        var reads = 0
        val fixture = fixture {
            reads++
            if (reads == 1) throw IOException("timer file temporarily unavailable")
            TimerSession()
        }
        fixture.vm.open("new")
        runCurrent()
        assertNotNull(fixture.vm.error.value)
        assertFalse(fixture.vm.loading.value)
        assertTrue(fixture.player.loads.isEmpty())
        fixture.vm.retry()
        runCurrent()
        assertEquals("Retry must rerun initialization rather than only clear the error", 2, reads)
        assertEquals(listOf(snapshot), fixture.player.loads)
        assertNull(fixture.vm.error.value)
        assertFalse(fixture.player.isPlaying.value)
        fixture.player.play()
        runCurrent()
        assertEquals(1, fixture.timer.starts.size)
        assertEquals(TimerStatus.RUNNING, fixture.holder.state.value.status)
        assertEquals("new", fixture.holder.state.value.sessionId)
        assertEquals(20_000L, fixture.holder.state.value.startedAtEpochMillis)
    }

    @Test fun unreadableRestoredVideoRetryPreparesOriginalSnapshotWithoutRestartingTimer() = runTest(dispatcher) {
        val original = TimerSession(status = TimerStatus.PAUSED, sessionId = "existing", kind = TimerKind.VIDEO,
            startedAtEpochMillis = 10_000L, elapsedMillis = 2_500L, video = snapshot)
        val fixture = fixture { original }
        fixture.files.readable = false
        fixture.vm.open("existing")
        runCurrent()
        assertNotNull(fixture.vm.error.value)
        assertTrue(fixture.player.loads.isEmpty())
        fixture.files.readable = true
        fixture.vm.retry()
        runCurrent()
        assertEquals(listOf(snapshot), fixture.player.loads)
        assertNull(fixture.vm.error.value)
        assertFalse(fixture.player.isPlaying.value)
        assertEquals(original.startedAtEpochMillis, fixture.holder.state.value.startedAtEpochMillis)
        assertEquals(original.elapsedMillis, fixture.holder.state.value.elapsedMillis)
        assertEquals(TimerStatus.PAUSED, fixture.holder.state.value.status)
        assertTrue(fixture.timer.starts.isEmpty())
    }

    @Test fun repeatedRetryTapsRunOnlyOnePendingInitialization() = runTest(dispatcher) {
        var reads = 0
        val pending = CompletableDeferred<Unit>()
        val fixture = fixture {
            reads++
            if (reads == 1) throw IOException("temporary")
            pending.await()
            TimerSession()
        }
        fixture.vm.open("new")
        runCurrent()
        fixture.vm.retry()
        fixture.vm.retry()
        runCurrent()
        assertEquals(2, reads)
        assertTrue(fixture.vm.loading.value)
        pending.complete(Unit)
        runCurrent()
        assertEquals(listOf(snapshot), fixture.player.loads)
        assertFalse(fixture.vm.loading.value)
    }

    @Test fun retryCannotTakeOverAnUnrelatedActiveTimer() = runTest(dispatcher) {
        val original = TimerSession(status = TimerStatus.RUNNING, sessionId = "other", kind = TimerKind.NORMAL,
            startedAtEpochMillis = 10_000L, resumedAtElapsedRealtime = 4_000L)
        val fixture = fixture { original }
        fixture.vm.open("new")
        runCurrent()
        assertNotNull(fixture.vm.error.value)
        fixture.vm.retry()
        runCurrent()
        assertNotNull("Retry must retain the conflicting-session error", fixture.vm.error.value)
        assertEquals(original, fixture.holder.state.value)
        assertTrue(fixture.player.loads.isEmpty())
        fixture.player.play()
        runCurrent()
        assertFalse(fixture.player.isPlaying.value)
        assertTrue(fixture.timer.starts.isEmpty())
    }

    private fun TestScope.fixture(readTimer: suspend () -> TimerSession): Fixture {
        val holder = TimerStateHolder()
        val files = Files()
        val player = Controller()
        val timer = TimerBoundary(holder, wall, elapsed)
        val grants = VideoGrantRegistry({ emptySet() }, files, DataMaintenanceGate(), backgroundScope)
        val saved = SavedStateHandle(mapOf("prepared_playback" to Json.encodeToString(snapshot)))
        val vm = VideoTimerViewModel(player, files, grants, timer, readTimer, holder, wall, elapsed, saved)
        val owned = ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(vm))
        })["video", VideoTimerViewModel::class.java]
        return Fixture(owned, holder, files, player, timer)
    }

    private data class Fixture(val vm: VideoTimerViewModel, val holder: TimerStateHolder,
        val files: Files, val player: Controller, val timer: TimerBoundary)

    private class Files : VideoFileAccess {
        var readable = true
        override suspend fun acquire(uriString: String, flags: Int) =
            Result.success(LocalVideoRef(uriString, "selected.mp4", "video/mp4"))
        override suspend fun check(video: LocalVideoRef) =
            if (readable) VideoAccessState.READABLE else VideoAccessState.MISSING
        override suspend fun releaseUnused(referencedUris: Set<String>) = Unit
    }

    private class Controller : VideoPlayerController {
        val loads = mutableListOf<VideoPlaybackSnapshot>()
        override val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) {
            _, _, _ -> error("Native renderer is not used by the ViewModel")
        } as Player
        override val playback = MutableStateFlow<VideoPlaybackSnapshot?>(null)
        override val interactions = MutableSharedFlow<VideoPlaybackSnapshot>()
        override val isPlaying = MutableStateFlow(false)
        override val error = MutableStateFlow<String?>(null)
        override fun load(snapshot: VideoPlaybackSnapshot) { pause(); loads += snapshot; playback.value = snapshot; error.value = null }
        override fun play() { isPlaying.value = true }
        override fun pause() { isPlaying.value = false }
        override fun seekTo(positionMillis: Long) { playback.value = playback.value?.copy(positionMillis = positionMillis) }
        override fun setSpeed(speed: Float) { playback.value = playback.value?.copy(speed = speed) }
        override fun setLoop(loop: Boolean) { playback.value = playback.value?.copy(loop = loop) }
        override fun release() { pause() }
    }

    /** Service boundary only; the actual timer-start policy still validates every request. */
    private class TimerBoundary(private val holder: TimerStateHolder, private val wall: Clock,
        private val elapsed: ElapsedRealtimeClock) : TimerController {
        val starts = mutableListOf<TimerStartRequest>()
        override val state = holder.state
        override fun restore() = Unit
        override fun start(request: TimerStartRequest) {
            starts += request
            holder.set(TimerSessionPolicy.start(state.value, request, wall.millis(), elapsed.millis(), 1))
        }
        override fun pause(sessionId: String) { holder.set(state.value.copy(status = TimerStatus.PAUSED)) }
        override fun resume(sessionId: String) { holder.set(state.value.copy(status = TimerStatus.RUNNING)) }
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long,
            candidate: TimerFinishCandidate?) = error("Not part of playback initialization")
        override fun confirmFinish(sessionId: String) = error("Not part of playback initialization")
        override fun cancelFinish(sessionId: String) = error("Not part of playback initialization")
        override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) {
            if (state.value.sessionId == sessionId) holder.set(state.value.copy(video = snapshot))
        }
        override fun reset(sessionId: String) { holder.set(TimerSession()) }
        override fun discard(sessionId: String) { holder.set(TimerSessionPolicy.discard(state.value, sessionId)) }
    }
}
