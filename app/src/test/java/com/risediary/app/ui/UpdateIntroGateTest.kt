package com.risediary.app.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.*
import com.risediary.app.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateIntroGateTest {
    @Test fun oldCompletedInstallationEntersUpdateIntro() = scenario(preferencesOf(first to true)) { _, vm ->
        assertEquals("UPDATE_INTRO", vm.state.value.name)
    }
    @Test fun freshInstallationStillEntersOnlyFirstRun() = scenario(emptyPreferences()) { _, vm ->
        assertEquals("ONBOARDING", vm.state.value.name)
    }
    @Test fun seenCampaignDoesNotRepeatOnOrdinaryStartup() =
        scenario(preferencesOf(first to true, seen to "2.0.0")) { _, vm ->
            assertEquals("MAIN", vm.state.value.name)
        }
    @Test fun lockVerificationPrecedesTheUnseenUpdateIntro() =
        scenario(preferencesOf(first to true, lock to true, pin to "1234")) { _, vm ->
            assertEquals("LOCKED", vm.state.value.name)
            vm.onCredentialVerified("1234")
            runCurrent()
            assertEquals("UPDATE_INTRO", vm.state.value.name)
        }
    @Test fun unreadableLaunchFlagsFailClosed() = scenario(emptyPreferences(), true) { _, vm ->
        assertEquals("ERROR", vm.state.value.name)
    }
    @Test fun finishingTheIntroKeepsItsVerifiedCredentialWithoutAnExtraUnlock() =
        scenario(preferencesOf(first to true, lock to true, pin to "1234")) { prefs, vm ->
            vm.onCredentialVerified("1234"); runCurrent()
            prefs.finishUpdateIntro(); vm.onUpdateIntroFinished(); runCurrent()
            assertEquals("MAIN", vm.state.value.name)
        }
    @Test fun backgroundLockRetainsTheIntroAndUnlockReturnsToIt() =
        scenario(preferencesOf(first to true, lock to true, pin to "1234")) { prefs, vm ->
            prefs.setBackgroundAutoLockEnabled(true)
            vm.onCredentialVerified("1234"); runCurrent()
            vm.onAppMovedToBackground(); advanceTimeBy(400); runCurrent()
            assertEquals("LOCKED", vm.state.value.name)
            assertTrue(vm.retainUpdateIntroContent.value)
            vm.onAppReturnedToForeground(); vm.onCredentialVerified("1234"); runCurrent()
            assertEquals("UPDATE_INTRO", vm.state.value.name)
        }
    @Test fun completionInBackgroundWaitsForForegroundBeforeRouting() =
        scenario(preferencesOf(first to true)) { prefs, vm ->
            vm.onAppMovedToBackground()
            prefs.finishUpdateIntro(); vm.onUpdateIntroFinished(); runCurrent()
            assertEquals("UPDATE_INTRO", vm.state.value.name)
            vm.onAppReturnedToForeground(); runCurrent()
            assertEquals("MAIN", vm.state.value.name)
        }
    @Test fun completingBehindTheLockNeverUnlocksTheApp() =
        scenario(preferencesOf(first to true, lock to true, pin to "1234")) { prefs, vm ->
            prefs.setBackgroundAutoLockEnabled(true)
            vm.onCredentialVerified("1234"); runCurrent()
            vm.onAppMovedToBackground(); advanceTimeBy(400); runCurrent()
            prefs.finishUpdateIntro(); vm.onUpdateIntroFinished(); runCurrent()
            vm.onAppReturnedToForeground(); runCurrent()
            assertEquals("LOCKED", vm.state.value.name)
            vm.onCredentialVerified("1234"); runCurrent()
            assertEquals("MAIN", vm.state.value.name)
        }
    @Test fun replacementCredentialDuringIntroCannotReuseItsEarlierProof() =
        scenario(preferencesOf(first to true, lock to true, pin to "1234")) { prefs, vm ->
            vm.onCredentialVerified("1234"); runCurrent()
            prefs.setAppLock(true, "5678"); prefs.finishUpdateIntro()
            vm.onUpdateIntroFinished(); runCurrent()
            assertEquals("LOCKED", vm.state.value.name)
        }
    @Test fun debugReplayResetsOnlyTheCampaignAndRunsImmediately() =
        scenario(preferencesOf(first to true, seen to "2.0.0")) { prefs, vm ->
            prefs.setPredictionMaxTicks(123); prefs.setDetailVideoHiddenByDefault(true)
            vm.restartUpdateIntro(); runCurrent()
            assertEquals("UPDATE_INTRO", vm.state.value.name)
            assertNull(prefs.launchSettings.first().lastCompletedUpdateIntroId)
            assertTrue(prefs.securitySettings.first().onboardingCompleted)
            assertEquals(123, prefs.quantitySettings.first().predictionMaxTicks)
            assertTrue(prefs.detailVideoHiddenByDefault.first())
        }
    @Test fun debugReplayUsesCurrentVerifiedAccessButNeverBypassesBackgroundLock() =
        scenario(preferencesOf(first to true, seen to "2.0.0", lock to true, pin to "1234")) { prefs, vm ->
            prefs.setBackgroundAutoLockEnabled(true)
            vm.onCredentialVerified("1234"); runCurrent()
            vm.restartUpdateIntro(); runCurrent()
            assertEquals("UPDATE_INTRO", vm.state.value.name)
            vm.onAppMovedToBackground(); advanceTimeBy(400); runCurrent()
            assertEquals("LOCKED", vm.state.value.name)
        }
    @Test fun lockedAppCannotStartADebugReplay() =
        scenario(preferencesOf(first to true, seen to "2.0.0", lock to true, pin to "1234")) { prefs, vm ->
            vm.restartUpdateIntro(); runCurrent()
            assertEquals("LOCKED", vm.state.value.name)
            assertEquals("2.0.0", prefs.launchSettings.first().lastCompletedUpdateIntroId)
        }
    @Test fun failedDebugResetKeepsCompletionAndTheCurrentPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store(preferencesOf(first to true, seen to "2.0.0"), false)
        val prefs = UserPreferences(store, DataMaintenanceGate())
        val vm = AppGateViewModel(prefs, IdleTimer(), com.risediary.app.data.DatabaseReadiness {})
        try {
            runCurrent(); store.writeFailure = IOException("full")
            var errors = 0
            vm.restartUpdateIntro { errors++ }; runCurrent()
            assertEquals(1, errors)
            assertEquals("MAIN", vm.state.value.name)
            assertEquals("2.0.0", prefs.launchSettings.first().lastCompletedUpdateIntroId)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun delayedDebugResetDoesNotDismissABackgroundLockOrWriteTwice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = Store(preferencesOf(first to true, seen to "2.0.0", lock to true, pin to "1234"), false)
        val prefs = UserPreferences(store, DataMaintenanceGate())
        val vm = AppGateViewModel(prefs, IdleTimer(), com.risediary.app.data.DatabaseReadiness {})
        try {
            prefs.setBackgroundAutoLockEnabled(true)
            runCurrent(); vm.onCredentialVerified("1234"); runCurrent()
            val initialWrites = store.writes
            val barrier = CompletableDeferred<Unit>(); store.barrier = barrier
            vm.restartUpdateIntro(); runCurrent()
            vm.restartUpdateIntro(); vm.onAppMovedToBackground()
            advanceTimeBy(400); runCurrent()
            barrier.complete(Unit); runCurrent()
            assertEquals(initialWrites + 1, store.writes)
            assertEquals("LOCKED", vm.state.value.name)
            vm.onAppReturnedToForeground(); runCurrent()
            assertEquals("LOCKED", vm.state.value.name)
            vm.onCredentialVerified("1234"); runCurrent()
            assertEquals("UPDATE_INTRO", vm.state.value.name)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private fun scenario(initial: Preferences, unreadable: Boolean = false,
                         block: suspend TestScope.(UserPreferences, AppGateViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val prefs = UserPreferences(Store(initial, unreadable), DataMaintenanceGate())
        val vm = AppGateViewModel(prefs, IdleTimer(), com.risediary.app.data.DatabaseReadiness {})
        try { runCurrent(); block(prefs, vm) }
        finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private class Store(initial: Preferences, val unreadable: Boolean) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        var writeFailure: Exception? = null
        var barrier: CompletableDeferred<Unit>? = null
        var writes = 0
        override val data: Flow<Preferences> = flow { if (unreadable) throw IOException("read"); emitAll(state) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            barrier?.await(); writeFailure?.let { throw it }
            return transform(state.value).also { state.value = it; writes++ }
        }
    }
    private class IdleTimer : TimerController {
        override val state = MutableStateFlow(TimerSession())
        override fun restore() = Unit
        override fun start(request: TimerStartRequest) = Unit
        override fun pause(sessionId: String) = Unit
        override fun resume(sessionId: String) = Unit
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) = Unit
        override fun confirmFinish(sessionId: String) = Unit
        override fun cancelFinish(sessionId: String) = Unit
        override fun updatePlayback(sessionId: String, snapshot: com.risediary.app.media.VideoPlaybackSnapshot, immediate: Boolean) = Unit
        override fun discard(sessionId: String) = Unit
        override fun reset(sessionId: String) = Unit
    }
    companion object {
        private val first = booleanPreferencesKey("onboarding_completed")
        private val seen = stringPreferencesKey("last_completed_update_intro_id")
        private val lock = booleanPreferencesKey("app_lock_enabled")
        private val pin = stringPreferencesKey("app_lock_pin")
    }
}
