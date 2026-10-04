package com.risediary.app.ui.timer

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.UserPreferences
import com.risediary.app.service.*
import com.risediary.app.ui.form.RecordFormSessionStore
import com.risediary.app.ui.navigation3.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    private val stateHolder: TimerStateHolder,
    private val forms: RecordFormSessionStore,
    private val preferences: UserPreferences,
    private val wallClock: Clock,
    private val elapsedClock: ElapsedRealtimeClock
) : ViewModel() {
    val session = controller.state
    val persistenceError = stateHolder.persistenceError
    val commandError = stateHolder.commandError
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var finishing by mutableStateOf(false)
        private set
    var transferFailed by mutableStateOf(false)
        private set
    var nextRoute by mutableStateOf<Route?>(null)
        private set
    var handoffSession by mutableStateOf<TimerSession?>(null)
        private set
    var discardDisplaySession by mutableStateOf<TimerSession?>(null)
        private set
    var discarding by mutableStateOf(false)
        private set
    var discardComplete by mutableStateOf(false)
        private set
    private var awaitingFinish = false
    private var observedActive = false
    private var pendingFormId: String? = null
    private var delivered = false

    init {
        viewModelScope.launch {
            combine(session, persistenceError, commandError) { state, failed, message ->
                Triple(state, failed, message)
            }.collect { (current, failed, message) ->
                if (discarding && current.status == TimerStatus.IDLE && !failed && message == null) {
                    discardComplete = true
                    observedActive = false
                    error = null
                } else if (discarding && current.sessionId != discardDisplaySession?.sessionId &&
                    current.status != TimerStatus.IDLE) {
                    discarding = false
                    discardDisplaySession = null
                    observedActive = false
                    return@collect
                }
                if (awaitingFinish && (failed || message != null)) busy = false
                if (!discarding && !discardComplete && current.isTerminal && current.finishCandidate == null &&
                    (awaitingFinish || observedActive) && handoffSession == null) {
                    awaitingFinish = false
                    busy = false
                    openRecord()
                }
                if (current.isActive) observedActive = true
            }
        }
    }

    fun consumeRoute() { delivered = true; nextRoute = null }
    fun consumeDiscard() {
        discardComplete = false
        discarding = false
        discardDisplaySession = null
    }

    fun discardTimer(expectedSessionId: String? = session.value.sessionId, beforeSend: () -> Unit = {}) {
        val current = session.value
        val id = current.sessionId ?: return
        if (id != expectedSessionId || busy || finishing || discardComplete ||
            current.finishCandidate != null || (!current.isActive && !current.isTerminal)) return
        busy = true
        discarding = true
        discardDisplaySession = current
        error = null
        val wasFailed = persistenceError.value
        viewModelScope.launch {
            try {
                beforeSend()
                controller.discard(id)
                val outcome = withTimeout(6_000L) {
                    combine(session, persistenceError, commandError) { state, failed, message ->
                        Triple(state, failed, message)
                    }.first { (state, failed, message) ->
                        (state.sessionId != id && !failed) || (!wasFailed && failed) || message != null
                    }
                }
                check(outcome.first.status == TimerStatus.IDLE && !outcome.second)
                discardComplete = true
                observedActive = false
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                error = "未能终止，请重试"
            } catch (_: Exception) {
                error = "未能终止，请重试"
            } finally {
                // Keep the discard intent until a durable reply, including one arriving after timeout.
                busy = false
            }
        }
    }

    override fun onCleared() {
        if (!delivered) pendingFormId?.let(forms::discard)
    }
    fun retryPersistence() = controller.retryPersistence()

    private fun awaitCommand(
        action: () -> Unit,
        accepted: (TimerSession) -> Boolean,
        failureMessage: String
    ) {
        if (busy || finishing || discarding || discardComplete) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                action()
                val outcome = withTimeout(6_000L) {
                    combine(session, persistenceError, commandError) { state, failed, message ->
                        Triple(state, failed, message)
                    }.first { (state, failed, message) -> accepted(state) || state.isTerminal || failed || message != null }
                }
                check((accepted(outcome.first) || outcome.first.isTerminal) && !outcome.second)
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                if (!finishing) error = failureMessage
            } catch (_: Exception) {
                if (!finishing) error = failureMessage
            } finally {
                if (!finishing) busy = false
            }
        }
    }

    fun start() {
        if (session.value.status != TimerStatus.IDLE || persistenceError.value) return
        val request = TimerStartRequest(UUID.randomUUID().toString(), TimerKind.NORMAL,
            startedAtEpochMillis = wallClock.millis(), startedAtElapsedRealtime = elapsedClock.millis())
        awaitCommand({ controller.start(request) }, { it.sessionId == request.sessionId && it.isActive },
            "计时未能开始，请重试")
    }

    fun pause() {
        if (persistenceError.value) return
        session.value.takeIf { it.status == TimerStatus.RUNNING }?.sessionId?.let { id ->
            awaitCommand({ controller.pause(id) }, { it.sessionId == id && it.status == TimerStatus.PAUSED },
                "未能暂停，请重试")
        }
    }

    fun resume() {
        if (persistenceError.value || session.value.finishCandidate != null) return
        session.value.takeIf { it.status == TimerStatus.PAUSED }?.sessionId?.let { id ->
            awaitCommand({ controller.resume(id) }, { it.sessionId == id && it.status == TimerStatus.RUNNING },
                "未能继续，请重试")
        }
    }

    fun requestFinish() = requestFinish {}
    fun requestFinish(beforeSend: () -> Unit) {
        val current = session.value
        if (current.status != TimerStatus.PAUSED || current.finishCandidate != null || persistenceError.value) return
        current.sessionId?.let { id ->
            // Capture the click instant before waiting for either the player or the service.
            val wall = wallClock.millis()
            val mono = elapsedClock.millis()
            val candidate = TimerFinishPolicy.capture(current, wall, mono)
            awaitCommand({ beforeSend(); controller.requestFinish(id, wall, mono, candidate) },
                { it.sessionId == id && it.finishCandidate != null }, "未能准备结束，请重试")
        }
    }

    fun confirmFinish() {
        val current = session.value
        if (busy || persistenceError.value || current.finishCandidate == null) return
        current.sessionId?.let {
            busy = true
            finishing = true
            error = null
            awaitingFinish = true
            controller.confirmFinish(it)
        }
    }

    fun cancelFinish() {
        if (busy || persistenceError.value) return
        awaitingFinish = false
        finishing = false
        session.value.sessionId?.let { id ->
            awaitCommand({ controller.cancelFinish(id) },
                { it.sessionId == id && it.status == TimerStatus.PAUSED && it.finishCandidate == null },
                "未能取消，请重试")
        }
    }

    fun openRecord() {
        val finished = handoffSession ?: session.value
        if (!finished.isTerminal || finished.finishCandidate != null || busy || discarding || discardComplete || nextRoute != null) return
        handoffSession = finished
        finishing = true
        transferFailed = false
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val maximum = preferences.quantitySettings.first().predictionMaxTicks
                // Pin the complete form (including its video) before resetting the service.
                val form = forms.createFromTimer(finished, maximum)
                pendingFormId = form.formId
                val id = requireNotNull(finished.sessionId)
                if (persistenceError.value) {
                    // The service admits RETRY while a durable transition is waiting; RESET is ignored.
                    controller.retryPersistence()
                    val retried = withTimeout(6_000L) {
                        combine(persistenceError, commandError) { failed, message -> failed to message }
                            .first { (failed, message) -> !failed || message != null }
                    }
                    check(!retried.first)
                }
                if (session.value.sessionId == id) {
                    controller.reset(id)
                    val outcome = withTimeout(6_000L) {
                        combine(session, persistenceError, commandError) { state, failed, message ->
                            Triple(state, failed, message)
                        }.first { (state, failed, message) -> state.sessionId != id || failed || message != null }
                    }
                    check(outcome.first.sessionId != id && !outcome.second)
                }
                nextRoute = Route.RecordForm(false, 0L, 0L, form.formId)
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                transferFailed = true
                error = "未能打开填写页面，请重试"
            } catch (_: Exception) {
                transferFailed = true
                error = "未能打开填写页面，请重试"
            } finally {
                busy = false
            }
        }
    }
}
