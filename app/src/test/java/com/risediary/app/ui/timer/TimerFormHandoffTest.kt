package com.risediary.app.ui.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.service.*
import com.risediary.app.ui.form.RecordFormSessionStore
import com.risediary.app.ui.navigation3.Route
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerFormHandoffTest {
    @Test fun cancellingRunningNotificationFinishAcceptsTheRunningAcknowledgementWithoutTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            val running = fixture.holder.state.value.copy(status = TimerStatus.RUNNING,
                resumedAtElapsedRealtime = 30_000L)
            fixture.holder.set(running.copy(finishCandidate =
                TimerFinishPolicy.capture(running, 120_000L, 30_000L)))
            runCurrent()
            fixture.model.cancelFinish()
            runCurrent()
            assertEquals(TimerStatus.RUNNING, fixture.holder.state.value.status)
            assertNull(fixture.holder.state.value.finishCandidate)
            assertFalse("A successful running acknowledgement must release the controls", fixture.model.busy)
            advanceTimeBy(6_001L)
            runCurrent()
            assertNull("Successful cancellation must not later become a failure", fixture.model.error)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun lateDiscardAcknowledgementStillExitsAfterTheTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.holdDiscard = true
            fixture.model.discardTimer()
            runCurrent()
            advanceTimeBy(6_001L)
            runCurrent()
            assertFalse(fixture.model.busy)
            assertFalse(fixture.model.discardComplete)
            fixture.controller.acknowledgeDiscard()
            runCurrent()
            assertTrue(fixture.model.discardComplete)
            assertNull(fixture.model.nextRoute)
            assertNull(fixture.model.handoffSession)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun retryOfAFailedWriteWaitsForTheNewDiscardAcknowledgement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.holder.setPersistenceError(true)
            fixture.controller.holdDiscard = true
            fixture.model.discardTimer()
            runCurrent()
            assertTrue(fixture.model.busy)
            assertFalse(fixture.model.discardComplete)
            fixture.controller.acknowledgeDiscard()
            runCurrent()
            assertTrue(fixture.model.discardComplete)
            assertNull(fixture.model.error)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun discardStopsVideoBeforeDispatchAndNeverCreatesAForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(withVideo = true)
        try {
            runCurrent()
            var mediaPaused = false
            fixture.model.discardTimer { mediaPaused = true }
            runCurrent()
            assertTrue(mediaPaused)
            assertEquals(TimerStatus.IDLE, fixture.holder.state.value.status)
            assertTrue(fixture.model.discardComplete)
            assertNull(fixture.model.nextRoute)
            assertNull(fixture.model.handoffSession)
            assertTrue(fixture.forms.videoUris().isEmpty())
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun repeatedDiscardWaitsForDurableAcknowledgement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.holdDiscard = true
            fixture.model.discardTimer()
            fixture.model.discardTimer()
            runCurrent()
            assertEquals(1, fixture.controller.discards)
            assertTrue(fixture.model.busy)
            assertFalse(fixture.model.discardComplete)
            fixture.controller.acknowledgeDiscard()
            runCurrent()
            assertFalse(fixture.model.busy)
            assertTrue(fixture.model.discardComplete)
            assertNull(fixture.model.nextRoute)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun failedDiscardPreservesTheTimerAndCanBeRetried() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.failDiscard = true
            fixture.model.discardTimer()
            runCurrent()
            assertEquals("s", fixture.holder.state.value.sessionId)
            assertFalse(fixture.model.busy)
            assertFalse(fixture.model.discardComplete)
            assertNotNull(fixture.model.error)
            fixture.controller.failDiscard = false
            fixture.model.discardTimer()
            runCurrent()
            assertTrue(fixture.model.discardComplete)
            assertFalse(fixture.holder.persistenceError.value)
            assertNull(fixture.model.nextRoute)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun limitReachedWhileDiscardIsPendingDoesNotOpenAForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.holdDiscard = true
            fixture.model.discardTimer()
            runCurrent()
            fixture.holder.set(fixture.holder.state.value.copy(status = TimerStatus.LIMIT_REACHED))
            runCurrent()
            assertNull(fixture.model.nextRoute)
            assertNull(fixture.model.handoffSession)
            fixture.controller.acknowledgeDiscard()
            runCurrent()
            assertTrue(fixture.model.discardComplete)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun cancellingConfirmationBlocksAnotherConfirmUntilAcknowledged() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.model.requestFinish()
            runCurrent()
            fixture.controller.holdCancel = true
            fixture.model.cancelFinish()
            fixture.model.confirmFinish()
            runCurrent()
            assertTrue(fixture.model.busy)
            assertEquals(0, fixture.controller.confirmations)
            fixture.controller.acknowledgeCancel()
            runCurrent()
            assertFalse(fixture.model.busy)
            assertEquals(TimerStatus.PAUSED, fixture.holder.state.value.status)
            assertNull(fixture.model.nextRoute)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun failedResetPersistenceRetriesThePendingWriteBeforeOpeningTheForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.failResetPersistence = true
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.confirmFinish()
            runCurrent()
            assertTrue(fixture.model.transferFailed)
            fixture.model.openRecord()
            runCurrent()
            assertNotNull(fixture.model.nextRoute)
            assertFalse(fixture.holder.persistenceError.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun repeatedFinishClicksWaitForTheServiceAcknowledgement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.holdRequest = true
            fixture.model.requestFinish()
            fixture.model.requestFinish()
            runCurrent()
            assertEquals(1, fixture.controller.requests)
            assertTrue(fixture.model.busy)
            fixture.controller.acknowledgeRequest()
            runCurrent()
            assertFalse(fixture.model.busy)
            fixture.model.confirmFinish()
            runCurrent()
            assertNotNull(fixture.model.nextRoute)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun resetTimeoutAllowsRetryOfTheSameForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.controller.holdReset = true
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.confirmFinish()
            runCurrent()
            val first = fixture.forms.createFromTimer(fixture.model.handoffSession!!, 80)
            advanceTimeBy(6_001L)
            runCurrent()
            assertTrue(fixture.model.transferFailed)
            assertFalse(fixture.model.busy)
            fixture.controller.holdReset = false
            fixture.model.openRecord()
            runCurrent()
            val route = fixture.model.nextRoute as Route.RecordForm
            assertEquals(first.formId, route.formSessionId)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun confirmedEndPinsVideoAndTimeBeforeResetAndOpensOneForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(withVideo = true)
        try {
            runCurrent()
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.confirmFinish()
            fixture.model.confirmFinish()
            runCurrent()
            val route = fixture.model.nextRoute as Route.RecordForm
            val form = requireNotNull(fixture.forms.get(requireNotNull(route.formSessionId)))
            assertEquals(100_000L, form.startTime)
            assertEquals(120_000L, form.endTime)
            assertEquals(8, form.durationSeconds)
            assertEquals(fixture.video, form.video)
            assertEquals("s", form.submissionId)
            assertEquals(1, fixture.controller.confirmations)
            assertEquals(1, fixture.controller.resets)
            assertEquals(TimerStatus.IDLE, fixture.holder.state.value.status)
            assertTrue(fixture.model.finishing)
            assertEquals(TimerActionState.OPENING, timerActionState(
                fixture.model.handoffSession!!, fixture.model.finishing, fixture.model.transferFailed))
            fixture.model.openRecord()
            runCurrent()
            assertEquals(route, fixture.model.nextRoute)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun cancelConfirmationLeavesPausedClockAndNoForm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            runCurrent()
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.cancelFinish()
            runCurrent()
            assertEquals(TimerStatus.PAUSED, fixture.holder.state.value.status)
            assertNull(fixture.holder.state.value.finishCandidate)
            assertNull(fixture.model.nextRoute)
            assertFalse(fixture.model.finishing)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun runningClockCannotOfferOrRequestFinish() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.holder.set(fixture.holder.state.value.copy(status = TimerStatus.RUNNING))
            runCurrent()
            fixture.model.requestFinish()
            assertEquals(0, fixture.controller.requests)
            assertNull(fixture.holder.state.value.finishCandidate)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun resetFailureCanRetryTheSameFormWithoutChangingTheEnd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.controller.failReset = true
            runCurrent()
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.confirmFinish()
            runCurrent()
            assertNull(fixture.model.nextRoute)
            assertTrue(fixture.model.transferFailed)
            val original = fixture.forms.createFromTimer(fixture.model.handoffSession!!, 80)
            fixture.forms.update(original.copy(moodNote = "retained"))
            fixture.controller.failReset = false
            fixture.model.openRecord()
            runCurrent()
            val route = fixture.model.nextRoute as Route.RecordForm
            val retried = fixture.forms.get(requireNotNull(route.formSessionId))!!
            assertEquals(original.formId, retried.formId)
            assertEquals(120_000L, retried.endTime)
            assertEquals("retained", retried.moodNote)
            assertEquals(2, fixture.controller.resets)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun delayedResetDisablesActionsAndDoesNotOfferIdleStart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.controller.holdReset = true
            runCurrent()
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.confirmFinish()
            runCurrent()
            assertTrue(fixture.model.busy)
            assertTrue(fixture.model.finishing)
            assertNull(fixture.model.nextRoute)
            fixture.model.confirmFinish()
            fixture.model.openRecord()
            assertEquals(1, fixture.controller.confirmations)
            assertEquals(1, fixture.controller.resets)
            fixture.controller.acknowledgeReset()
            runCurrent()
            assertNotNull(fixture.model.nextRoute)
            assertEquals(TimerStatus.FINISHED, fixture.model.handoffSession!!.status)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun persistenceRetryAfterConfirmAutomaticallyHandsOffOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        try {
            fixture.controller.failConfirm = true
            runCurrent()
            fixture.model.requestFinish()
            runCurrent()
            fixture.model.confirmFinish()
            runCurrent()
            assertTrue(fixture.holder.persistenceError.value)
            assertFalse(fixture.model.busy)
            assertNull(fixture.model.nextRoute)
            fixture.model.retryPersistence()
            runCurrent()
            assertNotNull(fixture.model.nextRoute)
            assertEquals(1, fixture.controller.confirmations)
            assertEquals(1, fixture.controller.resets)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    private class Fixture(withVideo: Boolean = false) {
        val video = LocalVideoRef("content://video/1", "video", "video/mp4")
        val holder = TimerStateHolder().apply { set(TimerSession(
            status = TimerStatus.PAUSED, sessionId = "s", startedAtEpochMillis = 100_000L,
            elapsedMillis = 8_000L, kind = if (withVideo) TimerKind.VIDEO else TimerKind.NORMAL,
            video = if (withVideo) VideoPlaybackSnapshot(this@Fixture.video) else null
        )) }
        val gate = DataMaintenanceGate()
        val forms = RecordFormSessionStore(gate)
        private val preferences = UserPreferences(object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }, gate)
        val controller = Boundary(holder, forms)
        val model = TimerViewModel(controller, holder, forms, preferences,
            Clock.fixed(Instant.ofEpochMilli(120_000L), ZoneOffset.UTC),
            object : ElapsedRealtimeClock { override fun millis() = 30_000L })
        fun close() = model.viewModelScope.cancel()
    }

    /** Only the service acknowledgement boundary is replaced; policies and form store are real. */
    private class Boundary(val holder: TimerStateHolder, val forms: RecordFormSessionStore) : TimerController {
        override val state = holder.state
        var discards = 0
        var holdDiscard = false
        var failDiscard = false
        private var discardId: String? = null
        override fun discard(sessionId: String) {
            discards++
            discardId = sessionId
            if (failDiscard) holder.setPersistenceError(true)
            else if (!holdDiscard) acknowledgeDiscard()
        }
        fun acknowledgeDiscard() {
            holder.setPersistenceError(false)
            holder.set(TimerSessionPolicy.discard(state.value, discardId))
        }
        var requests = 0
        var confirmations = 0
        var resets = 0
        var failReset = false
        var holdReset = false
        var failConfirm = false
        var holdRequest = false
        var holdCancel = false
        var failResetPersistence = false
        private var requestedCandidate: TimerFinishCandidate? = null
        override fun restore() = Unit
        override fun start(request: TimerStartRequest) = Unit
        override fun pause(sessionId: String) { holder.set(state.value.copy(status = TimerStatus.PAUSED)) }
        override fun resume(sessionId: String) { holder.set(state.value.copy(status = TimerStatus.RUNNING)) }
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) {
            requests++
            requestedCandidate = candidate
            if (!holdRequest) acknowledgeRequest()
        }
        fun acknowledgeRequest() { holder.set(state.value.copy(finishCandidate = requestedCandidate)) }
        override fun confirmFinish(sessionId: String) {
            confirmations++
            if (failConfirm) holder.setPersistenceError(true)
            else holder.set(TimerFinishPolicy.confirm(state.value))
        }
        override fun cancelFinish(sessionId: String) { if (!holdCancel) acknowledgeCancel() }
        fun acknowledgeCancel() { holder.set(TimerFinishPolicy.cancel(state.value, 130_000L, 40_000L)) }
        override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) = Unit
        override fun reset(sessionId: String) {
            resets++
            if (holder.persistenceError.value) return
            if (failResetPersistence) {
                holder.setPersistenceError(true)
                return
            }
            assertTrue("Video must already belong to the form before the service clears it",
                state.value.video?.video?.uriString?.let { it in forms.videoUris() } ?: true)
            if (failReset) holder.setCommandError("reset failed")
            else {
                holder.setCommandError(null)
                holder.setPersistenceError(false)
                if (!holdReset) acknowledgeReset()
            }
        }
        fun acknowledgeReset() { holder.set(TimerSession()) }
        override fun retryPersistence() {
            failConfirm = false
            holder.setPersistenceError(false)
            if (state.value.isTerminal) acknowledgeReset()
            else holder.set(TimerFinishPolicy.confirm(state.value))
        }
    }
}
