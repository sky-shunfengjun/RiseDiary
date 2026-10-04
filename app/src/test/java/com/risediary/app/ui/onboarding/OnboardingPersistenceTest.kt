package com.risediary.app.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPersistenceTest {
    @Test
    fun profileWriteFailureKeepsPageCallbackPendingAndCanRetrySameDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FailingStore()
        val preferences = UserPreferences(store, DataMaintenanceGate())
        val vm = OnboardingViewModel(preferences)
        try {
            var advanced = false
            store.writeFailure = IOException("storage unavailable")
            vm.saveProfile("草稿名字") { advanced = true }
            runCurrent()
            assertFalse(advanced)
            assertNotNull(vm.errorMessage.value)
            assertEquals("机长", preferences.username.first())
            store.writeFailure = null
            vm.saveProfile("草稿名字") { advanced = true }
            runCurrent()
            assertTrue(advanced)
            assertEquals("草稿名字", preferences.username.first())
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun recordingWriteFailureKeepsMaximumDraftAndCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FailingStore().also { it.writeFailure = IOException("storage unavailable") }
        val prefs = UserPreferences(store, DataMaintenanceGate())
        val vm = OnboardingViewModel(prefs)
        try {
            var advanced = false
            vm.saveRecordingPreferences(123) { advanced = true }
            runCurrent()
            assertFalse(advanced)
            assertNotNull(vm.errorMessage.value)
            assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
            store.writeFailure = null
            vm.saveRecordingPreferences(123) { advanced = true }
            runCurrent()
            assertTrue(advanced)
            assertEquals(123, prefs.quantitySettings.first().predictionMaxTicks)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun themeWriteFailureDoesNotAdvanceOrReplaceStoredTheme() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FailingStore().also { it.writeFailure = IOException("storage unavailable") }
        val preferences = UserPreferences(store, DataMaintenanceGate())
        val vm = OnboardingViewModel(preferences)
        try {
            var advanced = false
            vm.saveTheme("dark") { advanced = true }
            runCurrent()
            assertFalse(advanced)
            assertNotNull(vm.errorMessage.value)
            assertEquals("system", preferences.themeMode.first())
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun maintenanceBusyFinishDoesNotCompleteOnboarding() = runTest {
        val gate = DataMaintenanceGate()
        val preferences = UserPreferences(FailingStore(), gate)
        val vm = OnboardingViewModel(preferences)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val operation = async {
            gate.maintenance {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        try {
            assertEquals(false, vm.finish(firstRun = true, reminderTime = null))
            assertNotNull(vm.errorMessage.value)
            assertFalse(preferences.securitySettings.first().onboardingCompleted)
        } finally {
            release.complete(Unit)
            operation.await()
        }
    }
    @Test
    fun successfulFinishReportsSuccessOnlyAfterWritingOnboardingMarker() = runTest {
        val preferences = UserPreferences(FailingStore(), DataMaintenanceGate())
        val vm = OnboardingViewModel(preferences)
        assertEquals(true, vm.finish(firstRun = true, reminderTime = null))
        assertTrue(preferences.securitySettings.first().onboardingCompleted)
    }

    @Test
    fun failedFinishReportsFailureAndLeavesOnboardingIncomplete() = runTest {
        val store = FailingStore().also { it.writeFailure = IOException("storage unavailable") }
        val preferences = UserPreferences(store, DataMaintenanceGate())
        val vm = OnboardingViewModel(preferences)
        val result = runCatching { vm.finish(firstRun = true, reminderTime = null) }.getOrNull()
        assertEquals(false, result)
        assertFalse(preferences.securitySettings.first().onboardingCompleted)
    }

    @Test
    fun cancellationStillPropagatesInsteadOfReportingSaveFailureOrSuccess() = runTest {
        val store = FailingStore().also { it.writeFailure = CancellationException("page left") }
        val vm = OnboardingViewModel(UserPreferences(store, DataMaintenanceGate()))
        val failure = runCatching { vm.finish(firstRun = true, reminderTime = null) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    private class FailingStore : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var writeFailure: Exception? = null
        override val data: Flow<Preferences> = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            val result = transform(values.value)
            values.value = result
            return result
        }
    }
}