package com.risediary.app.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerSessionStore
import com.risediary.app.service.TimerStateHolder
import com.risediary.app.service.TimerStatus
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
    private val stateHolder: TimerStateHolder
) : ViewModel() {
    val session: StateFlow<TimerSession> = stateHolder.state

    init {
        viewModelScope.launch {
            val restored = store.load()
            val current = stateHolder.state.value
            val effective = if (current.status == TimerStatus.IDLE) {
                stateHolder.set(restored)
                restored
            } else {
                current
            }
            if (effective.isActive) controller.restore()
        }
    }
}
