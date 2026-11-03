package com.risediary.app.ui.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.media.*
import com.risediary.app.service.*
import com.risediary.app.ui.form.RecordFormSessionStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFinishViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val wall = Clock.fixed(Instant.ofEpochMilli(108_000L), ZoneOffset.UTC)
    private val mono = object : ElapsedRealtimeClock { override fun millis() = 13_000L }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun finishWhileRunningCapturesClickBeforePausingMediaAndDeduplicates() = runTest(dispatcher) {
        val (vm, holder) = fixture(library = true)
        runCurrent()
        var mediaPauses = 0
        vm.requestLibraryFinish { mediaPauses++ }
        vm.requestLibraryFinish { mediaPauses++ }
        runCurrent()
        val pending = holder.state.value
        assertEquals(TimerStatus.RUNNING, pending.status)
        assertEquals(1, mediaPauses)
        assertEquals(108_000L, pending.finishCandidate?.requestedAtEpochMillis)
        assertEquals(8_000L, pending.finishCandidate?.elapsedMillis)
        val result = TimerFinishPolicy.confirm(pending)
        assertEquals(8_000L, result.elapsedMillis)
        assertEquals(VideoSourceRef.LibraryVideo("8ea76b7a-d676-4ab9-8e77-df20b843bc65"), result.videoSource)
        vm.cancelFinish(); runCurrent()
        assertNull(holder.state.value.finishCandidate)
        assertEquals(TimerStatus.RUNNING, holder.state.value.status)
    }

    @Test fun libraryShortcutCannotFinishPhoneTimerAndTheOriginalRunningActionStillRequiresPause() = runTest(dispatcher) {
        val (vm, holder) = fixture(library = false)
        runCurrent()
        vm.requestLibraryFinish { error("Must not pause another source") }
        vm.requestFinish { error("Original finish requires a paused timer") }
        runCurrent()
        assertNull(holder.state.value.finishCandidate)
        holder.set(holder.state.value.copy(status = TimerStatus.PAUSED, resumedAtElapsedRealtime = 0L))
        vm.requestFinish(); runCurrent()
        assertEquals(5_000L, holder.state.value.finishCandidate?.elapsedMillis)
    }

    private fun fixture(library: Boolean): Pair<TimerViewModel, TimerStateHolder> {
        val id = "8ea76b7a-d676-4ab9-8e77-df20b843bc65"
        val video = if (library) libraryVideoRef(id, "test.mp4") else LocalVideoRef("content://videos/one", "test.mp4", "video/mp4")
        val holder = TimerStateHolder().apply { set(TimerSession(status = TimerStatus.RUNNING, sessionId = "s",
            kind = TimerKind.VIDEO, startedAtEpochMillis = 100_000L, elapsedMillis = 5_000L,
            resumedAtElapsedRealtime = 10_000L, resumedAtWallClock = 105_000L,
            video = VideoPlaybackSnapshot(video), videoSource = if (library) VideoSourceRef.LibraryVideo(id) else VideoSourceRef.DeviceVideo(video))) }
        val boundary = object : TimerController {
            override val state = holder.state
            override fun restore() = Unit
            override fun start(request: TimerStartRequest) = Unit
            override fun pause(sessionId: String) = Unit
            override fun resume(sessionId: String) = Unit
            override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) {
                holder.set(TimerFinishPolicy.attach(state.value, requireNotNull(candidate)))
            }
            override fun cancelFinish(sessionId: String) { holder.set(TimerFinishPolicy.cancel(state.value, wall.millis(), mono.millis())) }
            override fun confirmFinish(sessionId: String) = Unit
            override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) = Unit
            override fun reset(sessionId: String) = Unit
            override fun discard(sessionId: String) = Unit
        }
        val prefs = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
        }
        val vm = TimerViewModel(boundary, holder, RecordFormSessionStore(), UserPreferences(prefs, DataMaintenanceGate()), wall, mono)
        val owned = ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(vm))
        })[TimerViewModel::class.java]
        return owned to holder
    }
}
