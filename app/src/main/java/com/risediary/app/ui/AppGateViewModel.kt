package com.risediary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.SecuritySettingsSnapshot
import com.risediary.app.data.UserPreferences
import com.risediary.app.service.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppGateState { LOADING, ONBOARDING, LOCKED, MAIN, ERROR }

internal fun resolveAppGate(snapshot: SecuritySettingsSnapshot, verifiedCredential: String? = null): AppGateState {
    if (snapshot.lockEnabled && !snapshot.credentialValid) return AppGateState.ERROR
    if (snapshot.lockEnabled && snapshot.credential != verifiedCredential) return AppGateState.LOCKED
    if (!snapshot.onboardingCompleted) return AppGateState.ONBOARDING
    return AppGateState.MAIN
}

@HiltViewModel
class AppGateViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val timerController: TimerController
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppGateState.LOADING)
    val state = mutableState.asStateFlow()
    private val mounted = MutableStateFlow(false)
    val retainMainContent = mounted.asStateFlow()
    val maintenanceState = preferences.maintenanceGate.state
    val dataWriteNotices = preferences.maintenanceGate.notices
    private var backgroundLockJob: Job? = null
    private var onboardingProof: String? = null
    private var readJob: Job? = null
    private var backgroundTransition = 0L

    init { retryRead() }

    fun retryRead() = readGate()
    fun onOnboardingFinished(verifiedCredential: String? = null) = readGate(verifiedCredential ?: onboardingProof)
    fun onCredentialVerified(credential: String) = readGate(credential)

    private fun readGate(proof: String? = null) {
        onAppReturnedToForeground()
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val result = try {
                preferences.maintenanceGate.state.first { it != com.risediary.app.data.DataMaintenanceGate.State.WORKING }
                preferences.maintenanceGate.write { resolveAppGate(preferences.securitySettings.first(), proof) }
            } catch (_: com.risediary.app.data.DataMaintenanceBusyException) { AppGateState.ERROR }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppGateState.ERROR }
            if (result == AppGateState.ONBOARDING) onboardingProof = proof
            if (result == AppGateState.MAIN) { mounted.value = true; onboardingProof = null }
            mutableState.value = result
        }
    }

    fun onAppMovedToBackground() {
        backgroundLockJob?.cancel()
        if (mutableState.value != AppGateState.MAIN) return
        val transition = ++backgroundTransition
        backgroundLockJob = viewModelScope.launch {
            delay(300L)
            try {
                preferences.maintenanceGate.state.first { it != com.risediary.app.data.DataMaintenanceGate.State.WORKING }
                val security = preferences.maintenanceGate.write { preferences.securitySettings.first() }
                val shouldLock = shouldLockOnBackground(
                    security.lockEnabled, security.credentialValid,
                    security.backgroundAutoLock, security.backgroundMode,
                    timerController.state.value.isActive
                )
                if (transition == backgroundTransition && shouldLock && mutableState.value == AppGateState.MAIN) {
                    mutableState.value = AppGateState.LOCKED
                }
            } catch (_: com.risediary.app.data.DataMaintenanceBusyException) {
                if (transition == backgroundTransition) mutableState.value = AppGateState.ERROR
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (transition == backgroundTransition) mutableState.value = AppGateState.ERROR
            }
        }
    }

    fun onAppReturnedToForeground() {
        backgroundTransition++
        backgroundLockJob?.cancel()
        backgroundLockJob = null
    }
}

internal fun shouldLockOnBackground(
    appLockEnabled: Boolean, credentialPresent: Boolean,
    backgroundAutoLockEnabled: Boolean, mode: BackgroundLockMode, timerActive: Boolean
): Boolean {
    if (!appLockEnabled || !credentialPresent || !backgroundAutoLockEnabled) return false
    return mode == BackgroundLockMode.ALWAYS || !timerActive
}

internal fun keepsMainContentMounted(state: AppGateState): Boolean =
    state == AppGateState.LOCKED || state == AppGateState.MAIN