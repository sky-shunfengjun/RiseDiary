package com.risediary.app.ui.updateintro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.*
import com.risediary.app.ui.onboarding.GuideMotionState
import com.risediary.app.ui.onboarding.GuideTransitionGate
import com.risediary.app.ui.policy.PolicyDocument
import com.risediary.app.util.PredictionQuantitySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Inputs, consent and animation are a memory session; only successful page saves are persisted. */
@HiltViewModel
class UpdateIntroViewModel @Inject constructor(private val preferences: UserPreferences) : ViewModel() {
    private val transitions = GuideTransitionGate()
    internal var motion = GuideMotionState(transitions, completePage = UpdateIntroStep.COMPLETE.ordinal)
        private set
    private val mutableUi = MutableStateFlow(UpdateIntroUiState())
    val ui = mutableUi.asStateFlow()
    private var readJob: Job? = null
    private var saveJob: Job? = null
    private var initialized = false
    private var snapshot: UpdateIntroSettingsSnapshot? = null
    private var dataGeneration: Long? = null
    private var session = 0L
    private var predictionChanged = false
    private var liveChanged = false
    private var videoChanged = false

    fun start() { if (readJob == null) retryRead() }

    fun endSession() {
        session++
        readJob?.cancel(); readJob = null
        saveJob?.cancel(); saveJob = null
        motion.cancelTransitions()
        motion = GuideMotionState(transitions, completePage = UpdateIntroStep.COMPLETE.ordinal)
        initialized = false; snapshot = null; dataGeneration = null
        predictionChanged = false; liveChanged = false; videoChanged = false
        mutableUi.value = UpdateIntroUiState()
    }

    fun retryRead() {
        readJob?.cancel()
        if (dataGeneration != null && dataGeneration != preferences.maintenanceGate.snapshotGeneration()) {
            initialized = false; dataGeneration = null
            predictionChanged = false; liveChanged = false; videoChanged = false
        }
        mutableUi.update { it.copy(ready = false, loading = true, readError = null, saveError = null) }
        val owner = session
        readJob = viewModelScope.launch {
            try {
                preferences.updateIntroSettings.collect { saved ->
                    if (owner != session) return@collect
                    val generation = preferences.maintenanceGate.snapshotGeneration()
                    if (dataGeneration != null && dataGeneration != generation) throw DataMaintenanceBusyException()
                    dataGeneration = generation
                    snapshot = saved
                    mutableUi.update { state ->
                        if (!initialized) {
                            initialized = true
                            state.copy(ready = true, loading = false, readError = null,
                                predictionMaxTicks = saved.predictionMaxTicks,
                                liveUpdatesEnabled = saved.liveUpdatesEnabled,
                                detailVideoHidden = saved.detailVideoHiddenByDefault)
                        } else state.copy(ready = true, loading = false, readError = null,
                            predictionMaxTicks = if (predictionChanged) state.predictionMaxTicks else saved.predictionMaxTicks,
                            liveUpdatesEnabled = if (liveChanged) state.liveUpdatesEnabled else saved.liveUpdatesEnabled,
                            detailVideoHidden = if (videoChanged) state.detailVideoHidden else saved.detailVideoHiddenByDefault)
                    }
                }
            } catch (_: DataMaintenanceBusyException) {
                if (owner == session) mutableUi.update { it.copy(ready = false, loading = false, readError = "数据已更新，请重新读取设置") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owner == session) mutableUi.update { it.copy(ready = false, loading = false, readError = "设置读取失败，请重试") }
            }
        }
    }

    private fun edit(step: UpdateIntroStep, block: (UpdateIntroUiState) -> UpdateIntroUiState) {
        val current = mutableUi.value
        if (current.step == step && current.ready && !current.saving && !current.transitioning &&
            !current.finished && !current.modalOpen) mutableUi.update(block)
    }

    fun setPrediction(value: Int) = edit(UpdateIntroStep.RECORDING_TIMER) {
        predictionChanged = true
        it.copy(predictionMaxTicks = PredictionQuantitySettings.normalizeStoredMaximum(value))
    }
    fun setLiveUpdates(value: Boolean) = edit(UpdateIntroStep.NOTIFICATIONS) {
        liveChanged = true; it.copy(liveUpdatesEnabled = value)
    }
    fun setVideoHidden(value: Boolean) = edit(UpdateIntroStep.VIDEO_PRIVACY) {
        videoChanged = true; it.copy(detailVideoHidden = value)
    }
    fun setAccepted(value: Boolean) {
        val state = mutableUi.value
        if (state.step == UpdateIntroStep.STATEMENT && !state.saving && !state.transitioning &&
            !state.finished && !state.modalOpen) mutableUi.update { it.copy(acceptedStatement = value) }
    }
    fun openDocument(document: PolicyDocument) {
        val state = mutableUi.value
        if (state.step == UpdateIntroStep.STATEMENT && !state.saving && !state.transitioning && !state.finished)
            mutableUi.update { it.copy(document = document) }
    }
    fun closeDocument() { mutableUi.update { it.copy(document = null) } }
    fun reportActionFailure() { mutableUi.update { it.copy(actionError = true) } }
    fun clearActionFailure() { mutableUi.update { it.copy(actionError = false) } }

    fun back(): Boolean {
        val state = mutableUi.value
        when {
            state.document != null -> closeDocument()
            state.saving || state.transitioning || state.finished -> return true
            state.step == UpdateIntroStep.SUCCESS -> return false
            else -> moveTo(UpdateIntroStep.entries[state.step.ordinal - 1])
        }
        return true
    }
    private fun moveTo(step: UpdateIntroStep) {
        motion.observeStep(step.ordinal)
        mutableUi.update { it.copy(step = step, transitioning = true, saveError = null, actionError = false) }
    }
    internal fun transitionSettled(id: Long, step: UpdateIntroStep) {
        if (step == mutableUi.value.step && motion.completeTransition(id, step.ordinal))
            mutableUi.update { it.copy(transitioning = false) }
    }
    fun next(mode: UpdateIntroMode, onFinished: () -> Unit = {}) {
        val state = mutableUi.value
        if (!state.canContinue) return
        if (state.step == UpdateIntroStep.SUCCESS || state.step == UpdateIntroStep.STATEMENT) {
            moveTo(UpdateIntroStep.entries[state.step.ordinal + 1])
            return
        }
        if (saveJob?.isActive == true) return
        val owner = session
        val generation = dataGeneration ?: return
        mutableUi.update { it.copy(saving = true, saveError = null) }
        saveJob = viewModelScope.launch {
            var finish = false
            try {
                preferences.maintenanceGate.write {
                    preferences.maintenanceGate.requireGeneration(generation)
                    when (state.step) {
                        UpdateIntroStep.RECORDING_TIMER -> if (predictionChanged && state.predictionMaxTicks != snapshot?.predictionMaxTicks)
                            preferences.setPredictionMaxTicks(state.predictionMaxTicks)
                        UpdateIntroStep.NOTIFICATIONS -> if (liveChanged && state.liveUpdatesEnabled != snapshot?.liveUpdatesEnabled)
                            preferences.setLiveUpdatesEnabled(state.liveUpdatesEnabled)
                        UpdateIntroStep.VIDEO_PRIVACY -> if (videoChanged && state.detailVideoHidden != snapshot?.detailVideoHiddenByDefault)
                            preferences.setDetailVideoHiddenByDefault(state.detailVideoHidden)
                        UpdateIntroStep.COMPLETE -> if (mode == UpdateIntroMode.AUTO) preferences.finishUpdateIntro()
                    }
                }
                if (owner == session) {
                    when (state.step) {
                        UpdateIntroStep.RECORDING_TIMER -> predictionChanged = false
                        UpdateIntroStep.NOTIFICATIONS -> liveChanged = false
                        UpdateIntroStep.VIDEO_PRIVACY -> videoChanged = false
                        else -> Unit
                    }
                    if (state.step == UpdateIntroStep.COMPLETE) {
                        mutableUi.update { it.copy(finished = true) }
                        finish = true
                    } else moveTo(UpdateIntroStep.entries[state.step.ordinal + 1])
                }
            } catch (_: DataMaintenanceBusyException) {
                if (owner == session) mutableUi.update { it.copy(ready = false, readError = "数据已更新，请重新读取设置", saveError = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owner == session) mutableUi.update { it.copy(saveError = "设置保存失败，请重试") }
            } finally {
                if (owner == session) mutableUi.update { it.copy(saving = false) }
            }
            // Navigation is outside persistence error handling; a written marker is never called an unsaved setting.
            if (finish && owner == session) onFinished()
        }
    }
}
