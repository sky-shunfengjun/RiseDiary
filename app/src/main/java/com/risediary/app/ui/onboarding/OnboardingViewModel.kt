/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.OnboardingSettingsSnapshot
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.UsernamePolicy
import com.risediary.app.ui.policy.PolicyDocument
import com.risediary.app.util.PredictionQuantitySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val preferences: UserPreferences) : ViewModel() {
    private val transitionGate = OnboardingTransitionGate()
    internal var motion = OnboardingMotionState(transitionGate)
        private set
    private val mutableUi = MutableStateFlow(OnboardingUiState())
    val ui = mutableUi.asStateFlow()
    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    val errorMessage = mutableErrorMessage.asStateFlow()
    private var readJob: Job? = null
    private var saveJob: Job? = null
    private var initialized = false
    private var usernameChanged = false
    private var themeChanged = false
    private var predictionChanged = false
    private var liveUpdatesChanged = false
    private var privacyChanged = false
    private var snapshot: OnboardingSettingsSnapshot? = null
    private var credentialProof: String? = null

    fun start() { if (readJob == null) retryRead() }
    fun endSession() {
        motion.cancelTransitions()
        motion = OnboardingMotionState(transitionGate)
        readJob?.cancel(); readJob = null
        saveJob?.cancel(); saveJob = null
        initialized = false
        usernameChanged = false; themeChanged = false; predictionChanged = false
        privacyChanged = false; liveUpdatesChanged = false
        credentialProof = null; snapshot = null
        mutableErrorMessage.value = null
        mutableUi.value = OnboardingUiState()
    }
    fun reportActionFailure(action: OnboardingPermissionAction) { mutableUi.update { it.copy(actionError = action) } }
    fun clearActionFailure() { mutableUi.update { it.copy(actionError = null) } }
    fun retryRead() {
        readJob?.cancel()
        mutableUi.update { it.copy(ready = false, loading = true, readError = null) }
        readJob = viewModelScope.launch {
            try {
                preferences.onboardingSettings.collect { saved ->
                    snapshot = saved
                    mutableUi.update { state ->
                        if (!initialized) {
                            initialized = true
                            state.copy(
                                ready = true, loading = false, readError = null,
                                username = saved.username, themeMode = saved.themeMode,
                                predictionMaxTicks = saved.predictionMaxTicks,
                                detailVideoHidden = saved.detailVideoHiddenByDefault,
                                liveUpdatesEnabled = saved.liveUpdatesEnabled,
                                appLockEnabled = saved.appLockEnabled, biometricEnabled = saved.biometricEnabled,
                            )
                        } else state.copy(
                            ready = true, loading = false, readError = null,
                            username = if (usernameChanged) state.username else saved.username,
                            themeMode = if (themeChanged) state.themeMode else saved.themeMode,
                            predictionMaxTicks = if (predictionChanged) state.predictionMaxTicks else saved.predictionMaxTicks,
                            detailVideoHidden = if (privacyChanged) state.detailVideoHidden else saved.detailVideoHiddenByDefault,
                            liveUpdatesEnabled = if (liveUpdatesChanged) state.liveUpdatesEnabled else saved.liveUpdatesEnabled,
                            appLockEnabled = saved.appLockEnabled, biometricEnabled = saved.biometricEnabled,
                        )
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableUi.update { it.copy(ready = false, loading = false, readError = "设置读取失败，请重试") }
            }
        }
    }

    private fun editDraft(step: OnboardingStep, edit: (OnboardingUiState) -> OnboardingUiState) {
        val state = mutableUi.value
        if (state.step == step && state.ready && !state.saving && !state.transitioning && !state.modalOpen) mutableUi.update(edit)
    }
    fun setAccepted(accepted: Boolean) {
        val state = mutableUi.value
        if (state.step == OnboardingStep.STATEMENT && !state.transitioning && !state.modalOpen) mutableUi.update { it.copy(acceptedStatement = accepted) }
    }
    fun setUsername(value: String) = editDraft(OnboardingStep.PROFILE) { usernameChanged = true; it.copy(username = UsernamePolicy.limit(value)) }
    fun setTheme(value: String) = editDraft(OnboardingStep.THEME) { themeChanged = true; it.copy(themeMode = value) }
    fun setPrediction(value: Int) = editDraft(OnboardingStep.PREDICTION) { predictionChanged = true; it.copy(predictionMaxTicks = PredictionQuantitySettings.normalizeStoredMaximum(value)) }
    fun setVideoHidden(value: Boolean) = editDraft(OnboardingStep.PRIVACY) { privacyChanged = true; it.copy(detailVideoHidden = value) }
    fun setLiveUpdates(value: Boolean) = editDraft(OnboardingStep.NOTIFICATIONS) { liveUpdatesChanged = true; it.copy(liveUpdatesEnabled = value) }

    fun openDocument(document: PolicyDocument) {
        if (!mutableUi.value.saving && !mutableUi.value.transitioning) mutableUi.update { it.copy(document = document) }
    }
    fun closeDocument() { mutableUi.update { it.copy(document = null) } }
    fun openLockSetup() = editDraft(OnboardingStep.PRIVACY) { if (it.appLockEnabled) it else it.copy(lockSetup = true) }
    fun closeLockSetup() { mutableUi.update { it.copy(lockSetup = false) } }
    fun recordCredential(proof: String) { credentialProof = proof }
    fun verifiedCredential(): String? = credentialProof
    internal fun transitionSettled(id: Long, step: OnboardingStep) {
        if (step == mutableUi.value.step && motion.completeTransition(id, step)) {
            mutableUi.update { it.copy(transitioning = false) }
        }
    }
    fun back(): Boolean {
        val state = mutableUi.value
        when {
            state.document != null -> closeDocument()
            state.lockSetup -> closeLockSetup()
            state.saving || state.transitioning || state.finished -> return true
            state.step == OnboardingStep.WELCOME -> return false
            else -> moveTo(OnboardingStep.entries[state.step.ordinal - 1])
        }
        return true
    }

    fun next(firstRun: Boolean, beforeSave: suspend () -> Unit = {}, onFinished: () -> Unit = {}): Job? {
        val state = mutableUi.value
        if (!state.canContinue) return null
        if (state.step == OnboardingStep.WELCOME || state.step == OnboardingStep.STATEMENT) { advance(state.step); return null }
        return saveSetting(onSaved = {
            when (state.step) {
                OnboardingStep.PROFILE -> { usernameChanged = false; mutableUi.update { it.copy(username = UsernamePolicy.normalize(state.username)) } }
                OnboardingStep.THEME -> themeChanged = false
                OnboardingStep.PREDICTION -> predictionChanged = false
                OnboardingStep.PRIVACY -> privacyChanged = false
                OnboardingStep.NOTIFICATIONS -> liveUpdatesChanged = false
                else -> Unit
            }
            if (state.step == OnboardingStep.COMPLETE) {
                mutableUi.update { it.copy(finished = true) }
                onFinished()
            } else advance(state.step)
        }, beforeSave = beforeSave) {
            when (state.step) {
                OnboardingStep.PROFILE -> if (state.username != snapshot?.username) preferences.setUsername(state.username)
                OnboardingStep.THEME -> if (state.themeMode != snapshot?.themeMode) preferences.setThemeMode(state.themeMode)
                OnboardingStep.PREDICTION -> if (state.predictionMaxTicks != snapshot?.predictionMaxTicks) preferences.setPredictionMaxTicks(state.predictionMaxTicks)
                OnboardingStep.PRIVACY -> if (privacyChanged) preferences.setDetailVideoHiddenByDefault(state.detailVideoHidden)
                OnboardingStep.NOTIFICATIONS -> if (liveUpdatesChanged && state.liveUpdatesEnabled != snapshot?.liveUpdatesEnabled) preferences.setLiveUpdatesEnabled(state.liveUpdatesEnabled)
                OnboardingStep.COMPLETE -> if (firstRun) preferences.finishOnboarding(true, null)
                OnboardingStep.WELCOME, OnboardingStep.STATEMENT -> Unit
            }
        }
    }
    private fun moveTo(step: OnboardingStep) {
        motion.observeStep(step)
        mutableUi.update { it.copy(step = step, transitioning = true, saveError = null, actionError = null) }
    }
    private fun advance(from: OnboardingStep) {
        if (mutableUi.value.step == from && from != OnboardingStep.COMPLETE) moveTo(OnboardingStep.entries[from.ordinal + 1])
    }
    fun saveProfile(username: String, onSaved: () -> Unit) = saveSetting(onSaved) { preferences.setUsername(username) }
    fun saveTheme(themeMode: String, onSaved: () -> Unit) = saveSetting(onSaved) { preferences.setThemeMode(themeMode) }
    fun saveRecordingPreferences(predictionMaxTicks: Int, onSaved: () -> Unit) = saveSetting(onSaved) { preferences.setPredictionMaxTicks(predictionMaxTicks) }

    private fun saveSetting(onSaved: () -> Unit, beforeSave: suspend () -> Unit = {}, action: suspend () -> Unit): Job {
        saveJob?.takeIf { it.isActive }?.let { return it }
        mutableErrorMessage.value = null
        mutableUi.update { it.copy(saving = true, saveError = null) }
        val generation = preferences.maintenanceGate.snapshotGeneration()
        val job = viewModelScope.launch {
            try {
                // Queued lock writes need their own permit; never join them while holding ours.
                beforeSave()
                preferences.maintenanceGate.write {
                    preferences.maintenanceGate.requireGeneration(generation)
                    action()
                }
                onSaved()
            }
            catch (error: DataMaintenanceBusyException) { reportSaveFailure(error) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { reportSaveFailure(error) }
            finally { mutableUi.update { it.copy(saving = false) } }
        }
        saveJob = job
        job.invokeOnCompletion { cause ->
            if (cause is DataMaintenanceBusyException) reportSaveFailure(cause)
            if (saveJob === job) mutableUi.update { it.copy(saving = false) }
        }
        return job
    }
    suspend fun finish(firstRun: Boolean, reminderTime: String?): Boolean = try {
        mutableErrorMessage.value = null
        preferences.finishOnboarding(firstRun, reminderTime)
        true
    } catch (error: DataMaintenanceBusyException) { reportSaveFailure(error); false }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { reportSaveFailure(error); false }

    internal fun reportSaveFailure(error: Exception) {
        val message = if (error is DataMaintenanceBusyException) "数据恢复或清除中，请稍后重试" else "设置保存失败，请重试"
        mutableErrorMessage.value = message
        mutableUi.update { it.copy(saveError = message) }
    }
}
