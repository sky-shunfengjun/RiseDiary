package com.risediary.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerStateHolder @Inject constructor() {
    private val mutableState = MutableStateFlow(TimerSession())
    val state: StateFlow<TimerSession> = mutableState.asStateFlow()

    fun set(session: TimerSession) {
        mutableState.value = session
    }
}
