package com.risediary.app.ui.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.risediary.app.service.BootIdentityProvider
import com.risediary.app.service.ElapsedRealtimeClock
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerSessionStore
import com.risediary.app.service.TimerStateHolder
import com.risediary.app.service.TimerStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.datastore.preferences.core.intPreferencesKey

@OptIn(ExperimentalCoroutinesApi::class)
class TimerCoordinatorRestoreRaceTest {
    @Test fun runningUpdateDuringLoadIsNotOverwritten() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            TimerCoordinatorViewModel(fixture.controller, fixture.store, fixture.holder)
            runCurrent()
            val live = TimerSession(status = TimerStatus.RUNNING, elapsedMillis = 45_000L)
            fixture.holder.set(live)
            fixture.disk.completeLoad()
            runCurrent()
            assertEquals("Late disk restore must not overwrite service state", live, fixture.holder.state.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun idleResetDuringLoadRejectsLateRestoreEvenWhenValueIsUnchanged() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            TimerCoordinatorViewModel(fixture.controller, fixture.store, fixture.holder)
            runCurrent()
            fixture.holder.set(TimerSession())
            fixture.disk.completeLoad()
            runCurrent()
            assertEquals("A reset is a new action even if the holder already says idle", TimerSession(), fixture.holder.state.value)
            assertEquals("Reset must not restart a foreground timer", 0, fixture.controller.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun runningThenIdleDuringLoadRejectsLateRestore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            TimerCoordinatorViewModel(fixture.controller, fixture.store, fixture.holder)
            runCurrent()
            fixture.holder.set(TimerSession(status = TimerStatus.RUNNING))
            fixture.holder.set(TimerSession())
            fixture.disk.completeLoad()
            runCurrent()
            assertEquals("Returning to idle must not let an old snapshot revive the timer", TimerSession(), fixture.holder.state.value)
            assertEquals(0, fixture.controller.restores)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun untouchedIdleHolderRestoresPersistedSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = Fixture()
            TimerCoordinatorViewModel(fixture.controller, fixture.store, fixture.holder)
            runCurrent()
            fixture.disk.completeLoad()
            runCurrent()
            assertEquals(TimerStatus.PAUSED, fixture.holder.state.value.status)
            assertEquals(30_000L, fixture.holder.state.value.elapsedMillis)
            assertEquals(1, fixture.controller.restores)
        } finally { Dispatchers.resetMain() }
    }

    private class Fixture {
        val holder = TimerStateHolder()
        val controller = TestController(holder.state)
        val disk = DeferredStore()
        // Exercise real load() using an isolated preferences store.
        val store = TimerSessionStore(disk, BootIdentityProvider { 1 },
            object : ElapsedRealtimeClock { override fun millis() = 0L },
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    private class DeferredStore : DataStore<Preferences> {
        override val data = MutableSharedFlow<Preferences>(replay = 1)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            error("Startup restore must only load")
        suspend fun completeLoad() {
            data.emit(preferencesOf(
                intPreferencesKey("duration_policy_version") to 2,
                stringPreferencesKey("session_id") to "restored",
                stringPreferencesKey("status") to TimerStatus.PAUSED.name,
                longPreferencesKey("elapsed_millis") to 30_000L
            ))
        }
    }

    private class TestController(override val state: StateFlow<TimerSession>) : TimerController {
        var restores = 0
        override fun restore() { restores++ }
        override fun start(request: com.risediary.app.service.TimerStartRequest) = Unit
        override fun pause(sessionId: String) = Unit
        override fun resume(sessionId: String) = Unit
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: com.risediary.app.service.TimerFinishCandidate?) = Unit
        override fun confirmFinish(sessionId: String) = Unit
        override fun cancelFinish(sessionId: String) = Unit
        override fun updatePlayback(sessionId: String, snapshot: com.risediary.app.media.VideoPlaybackSnapshot, immediate: Boolean) = Unit
        override fun discard(sessionId: String) = Unit
        override fun reset(sessionId: String) = Unit
    }

}
