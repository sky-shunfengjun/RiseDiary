@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.data.*
import com.risediary.app.ui.form.RecordFormSessionStore
import com.risediary.app.media.*
import com.risediary.app.service.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Real Media3 -> VideoTimerViewModel -> isolated durable timer + memory form; no user records. */
class VideoTimerFlowTest {
    @Test fun firstPlaybackPauseSeekLoopAndFinishProduceOneMemoryTimedForm() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = File(base.cacheDir, "video-timer-flow-" + UUID.randomUUID())
        val context = object : ContextWrapper(base) { override fun getFilesDir() = files }
        // Warm the generated fixture using IO before creating the surface/player.
        context.contentResolver.openFileDescriptor(TestVideoProvider.READABLE, "r")!!.use { }
        val clock = TestClocks()
        val timerStore = TimerSessionStore(context, BootIdentityProvider { 1 }, clock, clock.wall)
        val holder = TimerStateHolder()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val gate = DataMaintenanceGate()
        val forms = RecordFormSessionStore(gate)
        val access = AndroidVideoFileAccess(context)
        val grantScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val grants = VideoGrantRegistry({ database.flightDao().getVideoUris().toSet() +
            listOfNotNull(timerStore.load().video?.video?.uriString) }, access, gate, grantScope)
        val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val fixture = DurableTimerBoundary(timerStore, holder, clock, commandScope)
        val video = LocalVideoRef(TestVideoProvider.READABLE.toString(), "测试视频", "video/mp4")
        val models = ViewModelStore()
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        var model: VideoTimerViewModel? = null
        try {
            withContext(Dispatchers.Main) {
                model = VideoTimerViewModel(context, access, grants, fixture, timerStore, holder,
                    clock.wall, clock, SavedStateHandle(mapOf("prepared_playback" to
                        Json.encodeToString(VideoPlaybackSnapshot(video)))))
                models.put("video", model!!)
                model!!.open("session")
                texture = SurfaceTexture(0).apply { setDefaultBufferSize(320, 240) }
                surface = Surface(texture!!)
                model!!.controller.player.setVideoSurface(surface)
            }
            withTimeout(8_000L) { while (withContext(Dispatchers.Main) { model!!.loading.value }) delay(10) }
            assertEquals(TimerStatus.IDLE, timerStore.load().status)
            withContext(Dispatchers.Main) { model!!.controller.play() }
            withTimeout(8_000L) { while (holder.state.value.sessionId != "session") delay(10) }
            assertEquals(100_000L, timerStore.load().startedAtEpochMillis)
            withContext(Dispatchers.Main) {
                clock.mono = 12_000L; clock.wallMillis = 102_000L
                model!!.pause()
                model!!.controller.seekTo(500L)
                model!!.controller.setSpeed(2f)
                model!!.controller.setLoop(true)
                model!!.controller.play()
            }
            delay(150L)
            assertEquals(TimerStatus.RUNNING, timerStore.load().status)
            assertEquals(100_000L, timerStore.load().startedAtEpochMillis)
            withContext(Dispatchers.Main) { clock.mono = 13_000L; clock.wallMillis = 103_000L; fixture.pause("session") }
            withTimeout(5_000L) { while (holder.state.value.status != TimerStatus.PAUSED) delay(10) }
            assertEquals(3_000L, timerStore.load().elapsedMillis)
            assertFalse(withContext(Dispatchers.Main) { model!!.controller.isPlaying.value })
            withContext(Dispatchers.Main) {
                clock.mono = 23_000L; clock.wallMillis = 113_000L
                model!!.controller.play()
            }
            withTimeout(5_000L) { while (holder.state.value.status != TimerStatus.RUNNING) delay(10) }
            assertTrue(withContext(Dispatchers.Main) { model!!.controller.isPlaying.value })
            withContext(Dispatchers.Main) {
                clock.mono = 25_000L; clock.wallMillis = 115_000L
                val candidate = TimerFinishPolicy.capture(holder.state.value, 115_000L, 25_000L)
                fixture.requestFinish("session", 115_000L, 25_000L, candidate)
            }
            withTimeout(5_000L) { while (holder.state.value.finishCandidate == null) delay(10) }
            withContext(Dispatchers.Main) {
                clock.mono = 35_000L; clock.wallMillis = 125_000L
                fixture.confirmFinish("session")
            }
            withTimeout(5_000L) { while (!holder.state.value.isTerminal) delay(10) }
            val finished = holder.state.value
            assertEquals(5_000L, finished.elapsedMillis)
            assertEquals(115_000L, finished.endedAtEpochMillis)
            val form = forms.createFromTimer(finished, 80)
            assertEquals("session", form.submissionId)
            assertEquals(5, form.durationSeconds)
            assertEquals(115_000L, form.endTime)
            assertEquals(video, form.video)
            assertEquals(form, forms.get(form.formId))
            assertNull(RecordFormSessionStore(gate).get(form.formId))
            assertNull(database.recordDraftDao().getPending())
            assertEquals(TimerStatus.IDLE, timerStore.load().status)
            assertTrue(database.flightDao().getAll().isEmpty())
        } finally {
            withContext(Dispatchers.Main) { models.clear(); surface?.release(); texture?.release() }
            commandScope.cancel(); grantScope.cancel(); database.close()
        }
    }

    private class TestClocks : ElapsedRealtimeClock {
        @Volatile var mono = 10_000L
        @Volatile var wallMillis = 100_000L
        override fun millis() = mono
        val wall: Clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(wallMillis)
        }
    }
    // Substitute only the Android service boundary; the shared policies and stores remain real.
    private class DurableTimerBoundary(private val store: TimerSessionStore, private val holder: TimerStateHolder,
        private val clocks: TestClocks, private val scope: CoroutineScope) : TimerController {
        override val state = holder.state
        private val writes = Mutex()
        private fun write(build: () -> TimerSession) {
            scope.launch { writes.withLock { val value = build(); store.save(value); holder.set(value) } }
        }
        override fun restore() = Unit
        override fun start(request: TimerStartRequest) = write {
            TimerSessionPolicy.start(state.value, request, clocks.wallMillis, clocks.mono, 1)
        }
        override fun pause(sessionId: String) = write {
            TimerMath.advance(state.value, clocks.mono, clocks.wallMillis).copy(status = TimerStatus.PAUSED,
                resumedAtElapsedRealtime = 0L, resumedAtWallClock = 0L)
        }
        override fun resume(sessionId: String) = write {
            state.value.copy(status = TimerStatus.RUNNING, resumedAtElapsedRealtime = clocks.mono,
                resumedAtWallClock = clocks.wallMillis)
        }
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) =
            write { state.value.copy(finishCandidate = candidate ?: TimerFinishPolicy.capture(state.value, wallClockNow, elapsedRealtimeNow)) }
        override fun confirmFinish(sessionId: String) = write { TimerFinishPolicy.confirm(state.value) }
        override fun cancelFinish(sessionId: String) = write { TimerFinishPolicy.cancel(state.value, clocks.wallMillis, clocks.mono) }
        override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) =
            write { state.value.copy(video = snapshot) }
        override fun discard(sessionId: String) = Unit
        override fun reset(sessionId: String) = Unit
    }
}
