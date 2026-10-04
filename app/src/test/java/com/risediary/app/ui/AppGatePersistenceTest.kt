package com.risediary.app.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Tests real gate + strict preferences flow; the external persistence boundary can fail. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGatePersistenceTest {
    @Test
    fun readFailureThenRetryReturnsToOriginalPinInsteadOfMainOrOnboarding() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = mutableListOf<AppGateViewModel>()
        try {
            val store = FailingPreferencesStore()
            val preferences = UserPreferences(store, DataMaintenanceGate())
            preferences.setAppLock(true, "1234")
            preferences.setOnboardingCompleted(true)
            store.failReads = true
            val gate = AppGateViewModel(preferences, IdleTimer()).also { owner.add(it) }
            runCurrent()
            assertEquals(AppGateState.ERROR, gate.state.value)
            store.failReads = false
            gate.retryRead()
            runCurrent()
            assertEquals(AppGateState.LOCKED, gate.state.value)
            assertEquals("1234", preferences.securitySettingsValue().credential)
        } finally {
            owner.forEach { it.viewModelScope.cancel() }
            Dispatchers.resetMain()
        }
    }

    @Test
    fun finishingOnboardingWithoutVerificationNeverUnlocksStoredPin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = mutableListOf<AppGateViewModel>()
        try {
            val preferences = UserPreferences(FailingPreferencesStore(), DataMaintenanceGate())
            preferences.setAppLock(true, "1234")
            val gate = AppGateViewModel(preferences, IdleTimer()).also { owner.add(it) }
            runCurrent()
            assertEquals(AppGateState.LOCKED, gate.state.value)
            preferences.setOnboardingCompleted(true)
            gate.onOnboardingFinished()
            runCurrent()
            assertEquals(AppGateState.LOCKED, gate.state.value)
            gate.onCredentialVerified("1234")
            runCurrent()
            assertEquals(AppGateState.MAIN, gate.state.value)
        } finally {
            owner.forEach { it.viewModelScope.cancel() }
            Dispatchers.resetMain()
        }
    }

    @Test
    fun proofForPreviousCredentialCannotUnlockReplacementCredential() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = mutableListOf<AppGateViewModel>()
        try {
            val preferences = UserPreferences(FailingPreferencesStore(), DataMaintenanceGate())
            preferences.setOnboardingCompleted(true)
            preferences.setAppLock(true, "1234")
            val gate = AppGateViewModel(preferences, IdleTimer()).also { owner.add(it) }
            runCurrent()
            preferences.setAppLock(true, "5678")
            gate.onCredentialVerified("1234")
            runCurrent()
            assertEquals(AppGateState.LOCKED, gate.state.value)
        } finally {
            owner.forEach { it.viewModelScope.cancel() }
            Dispatchers.resetMain()
        }
    }

    private suspend fun UserPreferences.securitySettingsValue() = securitySettings.first()

    private class FailingPreferencesStore : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var failReads = false
        override val data: Flow<Preferences> = flow {
            if (failReads) throw IOException("temporary read failure")
            emitAll(values)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val result = transform(values.value)
            values.value = result
            return result
        }
    }

    private class IdleTimer : TimerController {
        override val state = MutableStateFlow(TimerSession())
        override fun restore() = Unit
        override fun start(request: com.risediary.app.service.TimerStartRequest) = Unit
        override fun pause(sessionId: String) = Unit
        override fun resume(sessionId: String) = Unit
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: com.risediary.app.service.TimerFinishCandidate?) = Unit
        override fun confirmFinish(sessionId: String) = Unit
        override fun cancelFinish(sessionId: String) = Unit
        override fun updatePlayback(sessionId: String, snapshot: com.risediary.app.media.VideoPlaybackSnapshot, immediate: Boolean) = Unit
        override fun discard(sessionId: String) = Unit
        override fun reset(sessionId: String) = Unit
    }
}
