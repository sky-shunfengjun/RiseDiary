package com.risediary.app.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.*
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.service.*
import com.risediary.app.ui.form.RecordFormSessionStore
import com.risediary.app.ui.navigation3.Route
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeSelectEntryTest {
    @Test fun preparedEntryOpensWithoutWaitingForAnotherDiskRead() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.disk.blockReads = true
            fixture.model.open(FlightEntry.NORMAL)
            runCurrent()
            assertEquals(Route.Timer, fixture.model.nextRoute)
            assertFalse(fixture.model.busy)
        } finally { fixture.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun timerStartedAfterPreparationStillBlocksAnotherEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.holder.set(TimerSession(status = TimerStatus.RUNNING, sessionId = "other"))
            fixture.model.open(FlightEntry.VIDEO)
            runCurrent()
            assertEquals("other", fixture.model.currentTimer?.sessionId)
            assertNull(fixture.model.nextRoute)
        } finally { fixture.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun failedPreparationCanBeRetriedByOpeningTheEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(failFirstRead = true)
        try {
            runCurrent()
            fixture.model.open(FlightEntry.NORMAL)
            runCurrent()
            assertEquals(Route.Timer, fixture.model.nextRoute)
            assertNull(fixture.model.error)
        } finally { fixture.model.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private class Fixture(failFirstRead: Boolean = false) {
        val gate = DataMaintenanceGate()
        val disk = TimerDisk().apply { failRead = failFirstRead }
        val holder = TimerStateHolder()
        private val clock = Clock.fixed(Instant.ofEpochMilli(120_000L), ZoneOffset.UTC)
        private val controller = object : TimerController {
            override val state = holder.state
            override fun restore() = Unit
            override fun start(request: TimerStartRequest) = Unit
            override fun pause(sessionId: String) = Unit
            override fun resume(sessionId: String) = Unit
            override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) = Unit
            override fun confirmFinish(sessionId: String) = Unit
            override fun cancelFinish(sessionId: String) = Unit
            override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) = Unit
            override fun reset(sessionId: String) = Unit
            override fun discard(sessionId: String) = Unit
        }
        private val settings = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }
        val model = ModeSelectViewModel(RecordFormSessionStore(gate), UserPreferences(settings, gate),
            TimerSessionStore(disk, BootIdentityProvider { 1 },
                object : ElapsedRealtimeClock { override fun millis() = 20_000L }, clock),
            controller, holder, clock)
    }

    private class TimerDisk : DataStore<Preferences> {
        private var content = preferencesOf(intPreferencesKey("duration_policy_version") to 2)
        var blockReads = false
        var failRead = false
        override val data: Flow<Preferences> = flow {
            if (blockReads) awaitCancellation()
            if (failRead) { failRead = false; throw IOException("read") }
            emit(content)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(content).also { content = it }
    }
}
