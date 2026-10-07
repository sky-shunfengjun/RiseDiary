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

enum class AppGateState { LOADING, ONBOARDING, UPDATE_INTRO, LOCKED, MAIN, ERROR }

internal fun resolveAppGate(snapshot: SecuritySettingsSnapshot, verifiedCredential: String? = null): AppGateState {
    if (snapshot.lockEnabled && !snapshot.credentialValid) return AppGateState.ERROR
    if (snapshot.lockEnabled && snapshot.credential != verifiedCredential) return AppGateState.LOCKED
    if (!snapshot.onboardingCompleted) return AppGateState.ONBOARDING
    return AppGateState.MAIN
}

internal fun resolveAppLaunchGate(snapshot: com.risediary.app.data.AppLaunchSnapshot, proof: String? = null): AppGateState {
    val securityGate = resolveAppGate(snapshot.security, proof)
    return if (securityGate == AppGateState.MAIN && snapshot.updateIntroPending) AppGateState.UPDATE_INTRO else securityGate
}

@HiltViewModel
class AppGateViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val timerController: TimerController,
    private val databaseReadiness: com.risediary.app.data.DatabaseReadiness
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppGateState.LOADING)
    val state = mutableState.asStateFlow()
    private val mounted = MutableStateFlow(false)
    val retainMainContent = mounted.asStateFlow()
    private val introMounted = MutableStateFlow(false)
    val retainUpdateIntroContent = introMounted.asStateFlow()
    private var foreground = true
    private var introFinishPending = false
    private var restartPending = false
    private var restartProof: String? = null
    private var restartJob: Job? = null
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

    fun onUpdateIntroFinished() {
        introFinishPending = true
        if (foreground && mutableState.value == AppGateState.UPDATE_INTRO) readGate(onboardingProof)
    }

    /** Developer replay changes only the device-local campaign marker. MAIN is already authenticated. */
    fun restartUpdateIntro(onFailure: () -> Unit = {}) {
        if (!foreground || mutableState.value != AppGateState.MAIN || restartJob?.isActive == true) return
        restartJob = viewModelScope.launch {
            try {
                val security = preferences.maintenanceGate.write {
                    val current = preferences.launchSettings.first().security
                    if (current.lockEnabled && !current.credentialValid) error("Invalid lock settings")
                    preferences.resetUpdateIntroCompletion()
                    current
                }
                restartPending = true
                // Background lock may have covered MAIN while persistence was suspended.
                if (mutableState.value == AppGateState.MAIN) {
                    restartProof = security.credential.takeIf { security.lockEnabled }
                    if (foreground) readGate(restartProof)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onFailure() }
        }
    }

    private fun readGate(proof: String? = null) {
        backgroundTransition++
        backgroundLockJob?.cancel()
        backgroundLockJob = null
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val result = try {
                preferences.maintenanceGate.state.first { it != com.risediary.app.data.DataMaintenanceGate.State.WORKING }
                preferences.maintenanceGate.write {
                    databaseReadiness.ensureAvailable()
                    resolveAppLaunchGate(preferences.launchSettings.first(), proof)
                }
            } catch (_: com.risediary.app.data.DataMaintenanceBusyException) { AppGateState.ERROR }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AppGateState.ERROR }
            if (result == AppGateState.ONBOARDING || result == AppGateState.UPDATE_INTRO) onboardingProof = proof
            if (result == AppGateState.UPDATE_INTRO) {
                introMounted.value = true; restartPending = false; restartProof = null
            }
            if (result == AppGateState.MAIN) {
                mounted.value = true; onboardingProof = null
                introMounted.value = false; introFinishPending = false
                restartPending = false; restartProof = null
            }
            mutableState.value = result
        }
    }

    fun onAppMovedToBackground() {
        foreground = false
        backgroundLockJob?.cancel()
        val coveredState = mutableState.value
        if (coveredState != AppGateState.MAIN && coveredState != AppGateState.UPDATE_INTRO && readJob?.isActive != true) return
        val transition = ++backgroundTransition
        backgroundLockJob = viewModelScope.launch {
            delay(300L)
            try {
                preferences.maintenanceGate.state.first { it != com.risediary.app.data.DataMaintenanceGate.State.WORKING }
                val security = if (readJob?.isActive == true) {
                    // A slow verified gate read must be cancelled at the lock deadline.
                    val generation = preferences.maintenanceGate.snapshotGeneration()
                    preferences.securitySettings.first().also {
                        preferences.maintenanceGate.requireGeneration(generation)
                    }
                } else {
                    // Ordinary settings writes must finish before deciding whether to lock.
                    preferences.maintenanceGate.write { preferences.securitySettings.first() }
                }
                val shouldLock = shouldLockOnBackground(
                    security.lockEnabled, security.credentialValid,
                    security.backgroundAutoLock, security.backgroundMode,
                    timerController.state.value.isActive
                )
                if (transition == backgroundTransition && shouldLock &&
                    (mutableState.value == coveredState || mutableState.value == AppGateState.MAIN ||
                        mutableState.value == AppGateState.UPDATE_INTRO)) {
                    coverPendingRead(AppGateState.LOCKED)
                }
            } catch (_: com.risediary.app.data.DataMaintenanceBusyException) {
                if (transition == backgroundTransition) coverPendingRead(AppGateState.ERROR)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (transition == backgroundTransition) coverPendingRead(AppGateState.ERROR)
            }
        }
    }

    private fun coverPendingRead(state: AppGateState) {
        readJob?.cancel()
        onboardingProof = null
        restartProof = null
        mutableState.value = state
    }

    fun onAppReturnedToForeground() {
        foreground = true
        backgroundTransition++
        backgroundLockJob?.cancel()
        backgroundLockJob = null
        if (introFinishPending && mutableState.value == AppGateState.UPDATE_INTRO) readGate(onboardingProof)
        else if (restartPending && mutableState.value == AppGateState.MAIN) readGate(restartProof)
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