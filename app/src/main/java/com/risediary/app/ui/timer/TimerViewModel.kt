package com.risediary.app.ui.timer

import androidx.lifecycle.ViewModel
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    stateHolder: TimerStateHolder
) : ViewModel() {
    val session: StateFlow<TimerSession> = controller.state

    val persistenceError = stateHolder.persistenceError

    fun retryPersistence() = controller.retryPersistence()
    fun start() = controller.start()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun finish() = controller.finish()
    fun reset() = controller.reset()
}
