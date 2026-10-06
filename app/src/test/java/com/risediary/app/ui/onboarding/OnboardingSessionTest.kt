package com.risediary.app.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.ui.policy.PolicyDocument
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSessionTest {
    @Test fun anUnrelatedVisualCompletionCannotUnlockTheCurrentPage() = scenario { _, _, vm ->
        vm.next(true)
        vm.transitionSettled(-1L, OnboardingStep.STATEMENT)
        assertTrue(vm.ui.value.transitioning)
    }

    @Test fun leavingTheGuideCancelsUnsubmittedSaveAndClearsOnlyTheMemorySession() = scenario { store, prefs, vm ->
        reach(vm, OnboardingStep.PROFILE)
        vm.setUsername("退出前未提交")
        val barrier = CompletableDeferred<Unit>()
        store.writeBarrier = barrier
        vm.next(true)
        runCurrent()
        assertTrue(vm.ui.value.saving)
        vm.recordCredential("session-proof")
        vm.motion.startIntro(androidx.compose.ui.geometry.Offset.Zero)
        vm.motion.advanceBy(1800f)
        assertTrue(vm.motion.canContinue)
        vm.endSession()
        vm.start()
        runCurrent()
        barrier.complete(Unit)
        runCurrent()
        assertEquals(OnboardingStep.WELCOME, vm.ui.value.step)
        assertEquals("机长", prefs.username.first())
        assertFalse(vm.ui.value.acceptedStatement)
        assertNull(vm.verifiedCredential())
        assertFalse(prefs.securitySettings.first().onboardingCompleted)
        assertFalse(vm.motion.canContinue)
        assertEquals(0f, vm.motion.introElapsedMillis, 0f)
    }

    @Test fun reviewingExistingGuideDoesNotRewriteItsCompletionFlagOrUnchangedValues() = scenario { store, prefs, vm ->
        prefs.finishOnboarding(true, null)
        prefs.setUsername("旧用户")
        runCurrent()
        reach(vm, OnboardingStep.COMPLETE)
        val writes = store.successfulWrites
        var returned = 0
        vm.next(false, onFinished = { returned++ })
        runCurrent()
        assertEquals(1, returned)
        assertEquals(writes, store.successfulWrites)
        assertTrue(prefs.securitySettings.first().onboardingCompleted)
        assertEquals("旧用户", prefs.username.first())
    }

    @Test fun existingInactiveOnlyReminderDisplaysItsOwnTime() = scenario { _, prefs, vm ->
        prefs.setDailyReminder(false)
        prefs.setInactiveReminder(true)
        prefs.setReminderTime(com.risediary.app.reminder.ReminderType.INACTIVE, "08:15")
        runCurrent()
        assertEquals("08:15", prefs.inactiveReminderTime.first())
    }
    @Test fun queuedSecurityWritesFinishBeforeTheGuideAcquiresItsWritePermit() = scenario { _, prefs, vm ->
        reach(vm, OnboardingStep.PROFILE)
        vm.setUsername("不会死锁")
        val queued = launch(start = CoroutineStart.LAZY) { prefs.setThemeMode("dark") }
        vm.next(true, beforeSave = { queued.start(); queued.join() })
        runCurrent()
        assertEquals(OnboardingStep.THEME, vm.ui.value.step)
        assertEquals("不会死锁", prefs.username.first())
    }
    @Test fun statementAndTransitionBothGateAdvancement() = scenario { _, _, vm ->
        vm.next(true)
        vm.next(true)
        assertEquals(OnboardingStep.STATEMENT, vm.ui.value.step)
        vm.motion.advanceBy(500f)
        vm.transitionSettled(vm.motion.transitionId, OnboardingStep.STATEMENT)
        vm.next(true)
        assertEquals(OnboardingStep.STATEMENT, vm.ui.value.step)
        vm.setAccepted(true)
        vm.openDocument(PolicyDocument.TERMS)
        vm.next(true)
        assertEquals(OnboardingStep.STATEMENT, vm.ui.value.step)
        vm.back()
        assertTrue(vm.ui.value.acceptedStatement)
        step(vm)
        assertEquals(OnboardingStep.PROFILE, vm.ui.value.step)
    }

    @Test fun refreshedSettingsUpdateUntouchedFieldsButKeepEditedNickname() = scenario { _, prefs, vm ->
        reach(vm, OnboardingStep.PROFILE)
        vm.setUsername("仍在填写")
        prefs.setUsername("从其他入口保存")
        prefs.setThemeMode("dark")
        runCurrent()
        assertEquals("仍在填写", vm.ui.value.username)
        assertEquals("dark", vm.ui.value.themeMode)
    }

    @Test fun readFailureCannotSaveDisplayedDefaultsAndRetryLoadsActualValues() = scenario { store, prefs, vm ->
        prefs.setUsername("旧名字")
        prefs.setPredictionMaxTicks(123)
        store.readFailure = IOException("unavailable")
        vm.retryRead()
        runCurrent()
        assertFalse(vm.ui.value.ready)
        assertNotNull(vm.ui.value.readError)
        reach(vm, OnboardingStep.PROFILE)
        val writes = store.successfulWrites
        vm.setUsername("不应写入")
        vm.next(true)
        runCurrent()
        assertEquals(writes, store.successfulWrites)
        assertEquals(OnboardingStep.PROFILE, vm.ui.value.step)
        store.readFailure = null
        vm.retryRead()
        runCurrent()
        assertEquals("旧名字", vm.ui.value.username)
        assertEquals(123, vm.ui.value.predictionMaxTicks)
    }

    @Test fun saveFailureKeepsDraftAndOnlySuccessfulRetryAdvances() = scenario { store, prefs, vm ->
        reach(vm, OnboardingStep.PROFILE)
        vm.setUsername("新称呼")
        store.writeFailure = IOException("full")
        step(vm)
        assertEquals(OnboardingStep.PROFILE, vm.ui.value.step)
        assertEquals("新称呼", vm.ui.value.username)
        assertNotNull(vm.ui.value.saveError)
        store.writeFailure = null
        step(vm)
        assertEquals(OnboardingStep.THEME, vm.ui.value.step)
        assertEquals("新称呼", prefs.username.first())
    }

    @Test fun pendingSaveIgnoresRepeatedNextAndBackAndDraftEdits() = scenario { store, prefs, vm ->
        reach(vm, OnboardingStep.PROFILE)
        vm.setUsername("确定的值")
        val barrier = CompletableDeferred<Unit>()
        store.writeBarrier = barrier
        vm.next(true)
        runCurrent()
        vm.next(true)
        vm.back()
        vm.setUsername("迟到的输入")
        assertEquals("确定的值", vm.ui.value.username)
        barrier.complete(Unit)
        runCurrent()
        assertEquals(OnboardingStep.THEME, vm.ui.value.step)
        assertEquals("确定的值", prefs.username.first())
    }

    @Test fun reviewWithoutChangesPreservesCustomReminderConfiguration() = scenario { _, prefs, vm ->
        prefs.setDailyReminder(true)
        prefs.setInactiveReminder(false)
        prefs.setInactiveReminderDays(14)
        prefs.setReminderTime(com.risediary.app.reminder.ReminderType.INACTIVE, "08:15")
        runCurrent()
        reach(vm, OnboardingStep.NOTIFICATIONS)
        step(vm)
        assertTrue(prefs.dailyReminderEnabled.first())
        assertFalse(prefs.inactiveReminderEnabled.first())
        assertEquals(14, prefs.inactiveReminderDays.first())
        assertEquals("08:15", prefs.inactiveReminderTime.first())
    }

    @Test fun notificationChoiceSavesOnlyLiveUpdatesAndPreservesExistingReminders() = scenario { _, prefs, vm ->
        prefs.setDailyReminder(true)
        prefs.setInactiveReminder(false)
        prefs.setInactiveReminderDays(14)
        prefs.setReminderTime(com.risediary.app.reminder.ReminderType.DAILY, "19:20")
        runCurrent()
        reach(vm, OnboardingStep.NOTIFICATIONS)
        vm.setLiveUpdates(false)
        assertTrue(prefs.liveUpdatesEnabled.first())
        step(vm)
        assertFalse(prefs.liveUpdatesEnabled.first())
        assertTrue(prefs.dailyReminderEnabled.first())
        assertFalse(prefs.inactiveReminderEnabled.first())
        assertEquals(14, prefs.inactiveReminderDays.first())
        assertEquals("19:20", prefs.dailyReminderTime.first())
    }

    @Test fun failedNotificationSaveKeepsChoiceUntilRetrySucceeds() = scenario { store, prefs, vm ->
        reach(vm, OnboardingStep.NOTIFICATIONS)
        vm.setLiveUpdates(false)
        store.writeFailure = IOException("full")
        step(vm)
        assertEquals(OnboardingStep.NOTIFICATIONS, vm.ui.value.step)
        assertTrue(prefs.liveUpdatesEnabled.first())
        store.writeFailure = null
        step(vm)
        assertEquals(OnboardingStep.COMPLETE, vm.ui.value.step)
        assertFalse(prefs.liveUpdatesEnabled.first())
    }

    @Test fun notificationPageWithoutEditsDoesNotReenableDisabledLiveUpdates() = scenario { store, prefs, vm ->
        prefs.setLiveUpdatesEnabled(false)
        runCurrent()
        reach(vm, OnboardingStep.NOTIFICATIONS)
        val writesBefore = store.successfulWrites
        assertFalse(vm.ui.value.liveUpdatesEnabled)
        step(vm)
        assertFalse(prefs.liveUpdatesEnabled.first())
        assertEquals(writesBefore, store.successfulWrites)
    }

    @Test fun completionOnlyMarksFinishedAfterFinalWriteAndCanRetry() = scenario { store, prefs, vm ->
        reach(vm, OnboardingStep.COMPLETE)
        assertFalse(prefs.securitySettings.first().onboardingCompleted)
        var entered = 0
        store.writeFailure = IOException("full")
        vm.next(true, onFinished = { entered++ })
        runCurrent()
        assertEquals(0, entered)
        assertFalse(prefs.securitySettings.first().onboardingCompleted)
        store.writeFailure = null
        vm.next(true, onFinished = { entered++ })
        runCurrent()
        vm.next(true, onFinished = { entered++ })
        runCurrent()
        assertEquals(1, entered)
        assertTrue(prefs.securitySettings.first().onboardingCompleted)
    }

    @Test fun newSessionStartsAtWelcomeKeepsSavedNicknameAndDiscardsUnsubmittedTheme() = scenario { _, prefs, vm ->
        reach(vm, OnboardingStep.PROFILE)
        vm.setUsername("已保存")
        step(vm)
        vm.setTheme("dark")
        val reopened = OnboardingViewModel(prefs)
        try {
            reopened.start()
            runCurrent()
            assertEquals(OnboardingStep.WELCOME, reopened.ui.value.step)
            assertEquals("已保存", reopened.ui.value.username)
            assertEquals("system", reopened.ui.value.themeMode)
            assertFalse(reopened.ui.value.acceptedStatement)
            assertNull(reopened.verifiedCredential())
        } finally { reopened.viewModelScope.cancel() }
    }

    @Test fun privacyChoiceSavesOnNextAndDoesNotEnableOtherSecurityOptions() = scenario { _, prefs, vm ->
        reach(vm, OnboardingStep.PRIVACY)
        vm.setVideoHidden(true)
        assertFalse(prefs.detailVideoHiddenByDefault.first())
        step(vm)
        assertTrue(prefs.detailVideoHiddenByDefault.first())
        assertFalse(prefs.securitySettings.first().lockEnabled)
        assertFalse(prefs.securitySettings.first().biometricEnabled)
    }

    private fun scenario(block: suspend TestScope.(SessionStore, UserPreferences, OnboardingViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = SessionStore()
        val prefs = UserPreferences(store, DataMaintenanceGate())
        val vm = OnboardingViewModel(prefs)
        try { vm.start(); runCurrent(); block(store, prefs, vm) }
        finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private fun TestScope.step(vm: OnboardingViewModel) {
        vm.next(true)
        runCurrent()
        vm.motion.advanceBy(500f)
        vm.transitionSettled(vm.motion.transitionId, vm.ui.value.step)
    }
    private fun TestScope.reach(vm: OnboardingViewModel, target: OnboardingStep) {
        repeat(target.ordinal) {
            if (vm.ui.value.step == OnboardingStep.STATEMENT) vm.setAccepted(true)
            step(vm)
        }
        assertEquals(target, vm.ui.value.step)
    }
    private class SessionStore : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        var writeBarrier: CompletableDeferred<Unit>? = null
        var successfulWrites = 0
        override val data: Flow<Preferences> get() = flow { readFailure?.let { throw it }; emitAll(values) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeBarrier?.await()
            writeFailure?.let { throw it }
            val updated = transform(values.value)
            successfulWrites++
            values.value = updated
            return updated
        }
    }
}
