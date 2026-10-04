package com.risediary.app.ui

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

internal enum class FlightEntry { NORMAL, VIDEO, MANUAL }

@HiltViewModel
class ModeSelectViewModel @Inject constructor(
    private val forms: RecordFormSessionStore,
    private val preferences: UserPreferences,
    private val timerStore: TimerSessionStore,
    private val timer: TimerController,
    private val holder: TimerStateHolder,
    private val clock: Clock
) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var currentTimer by mutableStateOf<TimerSession?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var nextRoute by mutableStateOf<Route?>(null)
        private set

    private var entryPrepared = false
    private val preparation = viewModelScope.launch {
        try {
            holder.restoreIfIdle(timerStore::load)
            entryPrepared = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The explicit entry retries and reports a read failure instead of assuming idle.
        }
    }

    internal fun open(entry: FlightEntry) {
        if (busy || nextRoute != null) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                preparation.join()
                val current = if (entryPrepared) holder.state.value else holder.restoreIfIdle(timerStore::load)
                entryPrepared = true
                if (current.isActive) currentTimer = current
                else {
                    // Ended, unsubmitted content from an earlier process is not recovered.
                    if (current.isTerminal) clearTimer(current)
                    check(timer.state.value.status == TimerStatus.IDLE)
                    nextRoute = when (entry) {
                        FlightEntry.NORMAL -> Route.Timer
                        FlightEntry.VIDEO -> Route.VideoTimer(UUID.randomUUID().toString())
                        FlightEntry.MANUAL -> {
                            val maximum = preferences.quantitySettings.first().predictionMaxTicks
                            val form = forms.createManual(clock.millis(), maximum)
                            Route.RecordForm(false, 0L, 0L, form.formId)
                        }
                    }
                }
            } catch (_: com.risediary.app.data.DataMaintenanceBusyException) { error = "数据处理中，请稍后重试" }
            catch (cancelled: CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) error = "计时未能清理，请重试"
                else throw cancelled
            }
            catch (_: Exception) { error = "无法打开，请重试" }
            finally { busy = false }
        }
    }

    fun consumeRoute() { nextRoute = null }
    fun dismissError() { error = null }
    fun dismissCurrent() { currentTimer = null }
    fun returnToTimer() {
        currentTimer?.let { nextRoute = if (it.kind == TimerKind.VIDEO)
            Route.VideoTimer(requireNotNull(it.sessionId)) else Route.Timer }
        currentTimer = null
    }

    private suspend fun clearTimer(session: TimerSession) {
        val id = requireNotNull(session.sessionId)
        timer.reset(id)
        withTimeout(6_000L) { timer.state.first { it.sessionId != id } }
    }
}
