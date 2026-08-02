package com.risediary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.UserPreferences
import com.risediary.app.service.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppGateState {
    LOADING,
    ONBOARDING,
    LOCKED,
    MAIN
}

@HiltViewModel
class AppGateViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val timerController: TimerController
) : ViewModel() {
    private val _state = MutableStateFlow(AppGateState.LOADING)
    val state: StateFlow<AppGateState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = when {
                !preferences.onboardingCompleted.first() -> AppGateState.ONBOARDING
                preferences.appLockEnabled.first() &&
                    preferences.appLockPin.first().isNotEmpty() -> AppGateState.LOCKED
                else -> AppGateState.MAIN
            }
        }
    }

    fun showMain() {
        _state.value = AppGateState.MAIN
    }

    fun onAppMovedToBackground() {
        if (_state.value != AppGateState.MAIN) return
        viewModelScope.launch {
            val shouldLock = shouldLockOnBackground(
                appLockEnabled = preferences.appLockEnabled.first(),
                credentialPresent = preferences.appLockPin.first().isNotEmpty(),
                backgroundAutoLockEnabled = preferences.backgroundAutoLockEnabled.first(),
                mode = preferences.backgroundLockMode.first(),
                timerActive = timerController.state.value.isActive
            )
            if (shouldLock && _state.value == AppGateState.MAIN) {
                _state.value = AppGateState.LOCKED
            }
        }
    }
}

internal fun shouldLockOnBackground(
    appLockEnabled: Boolean,
    credentialPresent: Boolean,
    backgroundAutoLockEnabled: Boolean,
    mode: BackgroundLockMode,
    timerActive: Boolean
): Boolean {
    if (!appLockEnabled || !credentialPresent || !backgroundAutoLockEnabled) return false
    return mode == BackgroundLockMode.ALWAYS || !timerActive
}

internal fun keepsMainContentMounted(state: AppGateState): Boolean =
    state == AppGateState.LOCKED || state == AppGateState.MAIN
