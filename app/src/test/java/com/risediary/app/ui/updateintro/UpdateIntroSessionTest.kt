package com.risediary.app.ui.updateintro

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.*
import com.risediary.app.ui.policy.PolicyDocument
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateIntroSessionTest {
    @Test fun strictReadUsesExistingChoicesInsteadOfGuideDefaults() = scenario { _, prefs, vm ->
        prefs.setPredictionMaxTicks(123); prefs.setLiveUpdatesEnabled(false); prefs.setDetailVideoHiddenByDefault(true)
        runCurrent()
        assertEquals(123, vm.ui.value.predictionMaxTicks)
        assertFalse(vm.ui.value.liveUpdatesEnabled)
        assertTrue(vm.ui.value.detailVideoHidden)
        assertTrue(vm.ui.value.ready)
    }
    @Test fun statementCannotAdvanceUntilConfirmedAndReadingKeepsConfirmation() = scenario { _, _, vm ->
        reach(vm, UpdateIntroStep.STATEMENT)
        vm.next(UpdateIntroMode.AUTO); runCurrent()
        assertEquals(UpdateIntroStep.STATEMENT, vm.ui.value.step)
        vm.setAccepted(true); vm.openDocument(PolicyDocument.PRIVACY)
        vm.next(UpdateIntroMode.AUTO); runCurrent()
        assertEquals(PolicyDocument.PRIVACY, vm.ui.value.document)
        vm.back()
        assertNull(vm.ui.value.document)
        assertTrue(vm.ui.value.acceptedStatement)
        step(vm)
        assertEquals(UpdateIntroStep.RECORDING_TIMER, vm.ui.value.step)
    }
    @Test fun notificationGetsItsOwnPageBetweenRecordingAndVideo() = scenario { _, _, vm ->
        assertEquals(listOf("SUCCESS", "STATEMENT", "RECORDING_TIMER", "NOTIFICATIONS", "VIDEO_PRIVACY", "COMPLETE"),
            UpdateIntroStep.entries.map { it.name })
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        step(vm)
        assertEquals("NOTIFICATIONS", vm.ui.value.step.name)
    }
    @Test fun recordingPageCommitsOnlyItsMaximumAndCannotEditTheNotificationChoice() = scenario { store, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(120); vm.setLiveUpdates(false)
        assertTrue(vm.ui.value.liveUpdatesEnabled)
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
        val writes = store.writes
        step(vm)
        assertEquals(writes + 1, store.writes)
        assertEquals(120, prefs.quantitySettings.first().predictionMaxTicks)
        assertTrue(prefs.liveUpdatesEnabled.first())
        assertEquals("NOTIFICATIONS", vm.ui.value.step.name)
    }
    @Test fun failedMaximumSaveStaysOnItsPageAndRetainsTheInput() = scenario { store, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(150)
        store.writeFailure = IOException("full")
        step(vm)
        assertEquals(UpdateIntroStep.RECORDING_TIMER, vm.ui.value.step)
        assertNotNull(vm.ui.value.saveError)
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
        store.writeFailure = null
        step(vm)
        assertEquals(150, prefs.quantitySettings.first().predictionMaxTicks)
        assertTrue(prefs.liveUpdatesEnabled.first())
        assertEquals("NOTIFICATIONS", vm.ui.value.step.name)
    }
    @Test fun notificationChoiceSavesOnlyOnContinueAndDoesNotRewriteTheMaximum() = scenario { store, prefs, vm ->
        prefs.setPredictionMaxTicks(123); runCurrent()
        reach(vm, notificationStep())
        vm.setLiveUpdates(false); vm.setPrediction(30)
        assertEquals(123, vm.ui.value.predictionMaxTicks)
        assertTrue(prefs.liveUpdatesEnabled.first())
        val writes = store.writes
        step(vm)
        assertEquals(writes + 1, store.writes)
        assertFalse(prefs.liveUpdatesEnabled.first())
        assertEquals(123, prefs.quantitySettings.first().predictionMaxTicks)
        assertEquals(UpdateIntroStep.VIDEO_PRIVACY, vm.ui.value.step)
    }
    @Test fun notificationSaveFailureRetainsChoiceAndRetryCommitsOnce() = scenario { store, prefs, vm ->
        reach(vm, notificationStep())
        vm.setLiveUpdates(false)
        store.writeFailure = IOException("full")
        step(vm)
        assertEquals(notificationStep(), vm.ui.value.step)
        assertNotNull(vm.ui.value.saveError)
        assertFalse(vm.ui.value.liveUpdatesEnabled)
        assertTrue(prefs.liveUpdatesEnabled.first())
        val writes = store.writes
        store.writeFailure = null
        step(vm)
        assertEquals(writes + 1, store.writes)
        assertFalse(prefs.liveUpdatesEnabled.first())
    }
    @Test fun notificationReturnKeepsDraftWithoutSavingItFromTheRecordingPage() = scenario { _, prefs, vm ->
        reach(vm, notificationStep())
        vm.setLiveUpdates(false)
        vm.back()
        vm.motion.advanceBy(600f)
        vm.transitionSettled(vm.motion.transitionId, vm.ui.value.step)
        assertEquals(UpdateIntroStep.RECORDING_TIMER, vm.ui.value.step)
        assertFalse(vm.ui.value.liveUpdatesEnabled)
        step(vm)
        assertEquals(notificationStep(), vm.ui.value.step)
        assertTrue(prefs.liveUpdatesEnabled.first())
        assertFalse(vm.ui.value.liveUpdatesEnabled)
        step(vm)
        assertFalse(prefs.liveUpdatesEnabled.first())
    }
    @Test fun unchangedPagesDoNotRewritePreferences() = scenario { store, _, vm ->
        val writes = store.writes
        reach(vm, UpdateIntroStep.COMPLETE)
        assertEquals(writes, store.writes)
    }
    @Test fun hiddenVideoChoiceSavesWithoutChangingRecordingSettings() = scenario { _, prefs, vm ->
        reach(vm, UpdateIntroStep.VIDEO_PRIVACY)
        vm.setVideoHidden(true)
        step(vm)
        assertTrue(prefs.detailVideoHiddenByDefault.first())
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
        assertTrue(prefs.liveUpdatesEnabled.first())
    }
    @Test fun readFailureDisablesEditsAndCannotOverwriteStoredChoices() = scenario { store, prefs, vm ->
        prefs.setPredictionMaxTicks(123); runCurrent()
        store.readFailure = IOException("read")
        vm.retryRead(); runCurrent()
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(80); vm.next(UpdateIntroMode.AUTO); runCurrent()
        assertFalse(vm.ui.value.ready)
        assertNotNull(vm.ui.value.readError)
        assertEquals(UpdateIntroStep.RECORDING_TIMER, vm.ui.value.step)
        store.readFailure = null
        vm.retryRead(); runCurrent()
        assertEquals(123, vm.ui.value.predictionMaxTicks)
        assertTrue(vm.ui.value.ready)
    }
    @Test fun repeatedContinueDuringSaveCannotChangeThePendingPatch() = scenario { store, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(120)
        val barrier = CompletableDeferred<Unit>()
        store.barrier = barrier
        vm.next(UpdateIntroMode.AUTO); runCurrent()
        vm.next(UpdateIntroMode.AUTO); vm.back(); vm.setPrediction(30)
        assertEquals(120, vm.ui.value.predictionMaxTicks)
        barrier.complete(Unit); runCurrent()
        assertEquals(120, prefs.quantitySettings.first().predictionMaxTicks)
        assertEquals("NOTIFICATIONS", vm.ui.value.step.name)
    }
    @Test fun finalWriteFailureLeavesGuideUnseenAndAllowsOneRetry() = scenario { store, prefs, vm ->
        reach(vm, UpdateIntroStep.COMPLETE)
        var navigations = 0
        store.writeFailure = IOException("full")
        vm.next(UpdateIntroMode.AUTO) { navigations++ }; runCurrent()
        assertEquals(0, navigations)
        assertFalse(vm.ui.value.finished)
        assertTrue(prefs.launchSettings.first().updateIntroPending)
        store.writeFailure = null
        vm.next(UpdateIntroMode.AUTO) { navigations++ }; runCurrent()
        assertEquals(1, navigations)
        assertFalse(prefs.launchSettings.first().updateIntroPending)
        vm.next(UpdateIntroMode.AUTO) { navigations++ }; runCurrent()
        assertEquals(1, navigations)
    }
    @Test fun reviewCompletionNeitherClearsNorRewritesTheCampaignMarker() = scenario { store, prefs, vm ->
        prefs.finishUpdateIntro(); runCurrent()
        reach(vm, UpdateIntroStep.COMPLETE)
        val writes = store.writes
        var returned = false
        vm.next(UpdateIntroMode.REVIEW) { returned = true }; runCurrent()
        assertTrue(returned)
        assertEquals(writes, store.writes)
        assertEquals("2.0.0", prefs.launchSettings.first().lastCompletedUpdateIntroId)
    }
    @Test fun leavingCancelsPendingSaveAndStartsAFreshMemorySession() = scenario { store, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(120)
        val barrier = CompletableDeferred<Unit>(); store.barrier = barrier
        vm.next(UpdateIntroMode.AUTO); runCurrent()
        vm.endSession(); barrier.complete(Unit); runCurrent()
        vm.start(); runCurrent()
        assertEquals(UpdateIntroStep.SUCCESS, vm.ui.value.step)
        assertFalse(vm.ui.value.acceptedStatement)
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
        assertEquals(0f, vm.motion.introElapsedMillis, 0f)
    }
    @Test fun staleVisualCompletionCannotUnlockAnotherTransition() = scenario { _, _, vm ->
        vm.next(UpdateIntroMode.AUTO)
        vm.transitionSettled(-1, UpdateIntroStep.STATEMENT)
        assertTrue(vm.ui.value.transitioning)
        vm.motion.advanceBy(500f)
        vm.transitionSettled(vm.motion.transitionId, UpdateIntroStep.STATEMENT)
        assertFalse(vm.ui.value.transitioning)
    }
    @Test fun externalSettingRefreshKeepsEditedMaximumButRefreshesUntouchedSwitch() = scenario { _, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(110)
        prefs.setPredictionMaxTicks(40); prefs.setLiveUpdatesEnabled(false); runCurrent()
        assertEquals(110, vm.ui.value.predictionMaxTicks)
        assertFalse(vm.ui.value.liveUpdatesEnabled)
    }
    @Test fun dataReplacementCannotCommitOldGuideInput() = scenario { _, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(140)
        prefs.maintenanceGate.maintenance { prefs.applySettingsForMaintenance(prefs.settingsSnapshot(prefs.rawSnapshot())) }
        runCurrent(); vm.next(UpdateIntroMode.AUTO); runCurrent()
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
        assertEquals(UpdateIntroStep.RECORDING_TIMER, vm.ui.value.step)
    }
    @Test fun unchangedDataReplacementOffersReadRetryAndCanContinueAfterReload() = scenario { _, prefs, vm ->
        reach(vm, UpdateIntroStep.RECORDING_TIMER)
        vm.setPrediction(140)
        prefs.maintenanceGate.maintenance { prefs.applySettingsForMaintenance(prefs.settingsSnapshot(prefs.rawSnapshot())) }
        runCurrent(); vm.next(UpdateIntroMode.AUTO); runCurrent()
        assertFalse(vm.ui.value.ready)
        assertNotNull(vm.ui.value.readError)
        vm.retryRead(); runCurrent()
        assertEquals(80, vm.ui.value.predictionMaxTicks)
        step(vm)
        assertEquals("NOTIFICATIONS", vm.ui.value.step.name)
    }
    private fun notificationStep() = UpdateIntroStep.entries.first { it.name == "NOTIFICATIONS" }
    private fun scenario(block: suspend TestScope.(Store, UserPreferences, UpdateIntroViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store()
        val prefs = UserPreferences(store, DataMaintenanceGate())
        prefs.setOnboardingCompleted(true)
        val vm = UpdateIntroViewModel(prefs)
        try { vm.start(); runCurrent(); block(store, prefs, vm) }
        finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private suspend fun TestScope.step(vm: UpdateIntroViewModel) {
        vm.next(UpdateIntroMode.AUTO); runCurrent()
        vm.motion.advanceBy(600f)
        vm.transitionSettled(vm.motion.transitionId, vm.ui.value.step)
    }
    private suspend fun TestScope.reach(vm: UpdateIntroViewModel, target: UpdateIntroStep) {
        repeat(target.ordinal) { index ->
            if (vm.ui.value.step == UpdateIntroStep.STATEMENT) vm.setAccepted(true)
            step(vm)
            assertEquals(UpdateIntroStep.entries[index + 1], vm.ui.value.step)
        }
    }
    private class Store : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        var barrier: CompletableDeferred<Unit>? = null
        var writes = 0
        override val data: Flow<Preferences> = flow { readFailure?.let { throw it }; emitAll(values) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            barrier?.await(); writeFailure?.let { throw it }
            return transform(values.value).also { values.value = it; writes++ }
        }
    }
}
