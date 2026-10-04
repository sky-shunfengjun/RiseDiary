package com.risediary.app.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerSessionStore
import com.risediary.app.service.TimerStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores timer state without briefly starting a foreground service for idle sessions.
 * It also exposes a persisted limit-reached event to the app-level navigator.
 */
@HiltViewModel
class TimerCoordinatorViewModel @Inject constructor(
    private val controller: TimerController,
    private val store: TimerSessionStore,
    private val stateHolder: TimerStateHolder,
    private val forms: com.risediary.app.ui.form.RecordFormSessionStore = com.risediary.app.ui.form.RecordFormSessionStore()
) : ViewModel() {
    val session: StateFlow<TimerSession> = stateHolder.state

    fun isFormLive(id: String): Boolean = forms.get(id) != null

    init {
        viewModelScope.launch {
            try {
                val effective = stateHolder.restoreIfIdle(store::load)
                if (effective.isActive) controller.restore()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { stateHolder.setPersistenceError(true) }
        }
    }
}
