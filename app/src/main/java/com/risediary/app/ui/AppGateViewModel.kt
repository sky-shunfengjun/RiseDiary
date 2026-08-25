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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var backgroundLockJob: Job? = null
    private var backgroundTransition = 0L

    init {
        viewModelScope.launch {
            _state.value = try {
                when {
                    !preferences.onboardingCompleted.first() -> AppGateState.ONBOARDING
                    preferences.appLockEnabled.first() &&
                        preferences.appLockPin.first().isNotEmpty() -> AppGateState.LOCKED
                    else -> AppGateState.MAIN
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Never fall back to LOCKED: without a verified credential the
                // user would be permanently stuck on the lock screen. Onboarding
                // is self-healing and keeps all data.
                AppGateState.ONBOARDING
            }
        }
    }

    fun showMain() {
        onAppReturnedToForeground()
        _state.value = AppGateState.MAIN
    }

    fun onAppMovedToBackground() {
        backgroundLockJob?.cancel()
        backgroundLockJob = null
        if (_state.value != AppGateState.MAIN) return

        val transition = ++backgroundTransition
        backgroundLockJob = viewModelScope.launch {
            // System prompts and OEM app-lock screens can briefly stop the activity.
            // Wait before locking so a quick return cancels this transition.
            delay(BACKGROUND_LOCK_GRACE_MILLIS)
            val shouldLock = shouldLockOnBackground(
                appLockEnabled = preferences.appLockEnabled.first(),
                credentialPresent = preferences.appLockPin.first().isNotEmpty(),
                backgroundAutoLockEnabled = preferences.backgroundAutoLockEnabled.first(),
                mode = preferences.backgroundLockMode.first(),
                timerActive = timerController.state.value.isActive
            )
            if (
                transition == backgroundTransition &&
                shouldLock &&
                _state.value == AppGateState.MAIN
            ) {
                _state.value = AppGateState.LOCKED
            }
        }
    }

    fun onAppReturnedToForeground() {
        backgroundTransition++
        backgroundLockJob?.cancel()
        backgroundLockJob = null
    }

    private companion object {
        const val BACKGROUND_LOCK_GRACE_MILLIS = 300L
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
