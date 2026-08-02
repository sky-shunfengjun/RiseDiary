package com.risediary.app.ui

import com.risediary.app.data.BackgroundLockMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLockPolicyTest {
    @Test
    fun lockedStateKeepsMainContentMountedToPreserveNavigation() {
        assertTrue(keepsMainContentMounted(AppGateState.LOCKED))
        assertTrue(keepsMainContentMounted(AppGateState.MAIN))
        assertFalse(keepsMainContentMounted(AppGateState.LOADING))
        assertFalse(keepsMainContentMounted(AppGateState.ONBOARDING))
    }

    @Test
    fun alwaysModeLocksWhenAppLockAndBackgroundLockAreReady() {
        assertTrue(
            shouldLockOnBackground(
                appLockEnabled = true,
                credentialPresent = true,
                backgroundAutoLockEnabled = true,
                mode = BackgroundLockMode.ALWAYS,
                timerActive = true
            )
        )
    }

    @Test
    fun timerExceptionKeepsAnActiveTimerUnlocked() {
        assertFalse(
            shouldLockOnBackground(
                appLockEnabled = true,
                credentialPresent = true,
                backgroundAutoLockEnabled = true,
                mode = BackgroundLockMode.EXCEPT_WHILE_TIMER_ACTIVE,
                timerActive = true
            )
        )
    }

    @Test
    fun timerExceptionLocksWhenThereIsNoActiveTimer() {
        assertTrue(
            shouldLockOnBackground(
                appLockEnabled = true,
                credentialPresent = true,
                backgroundAutoLockEnabled = true,
                mode = BackgroundLockMode.EXCEPT_WHILE_TIMER_ACTIVE,
                timerActive = false
            )
        )
    }

    @Test
    fun disabledOrIncompleteLockNeverLocksOnBackground() {
        assertFalse(
            shouldLockOnBackground(
                appLockEnabled = false,
                credentialPresent = true,
                backgroundAutoLockEnabled = true,
                mode = BackgroundLockMode.ALWAYS,
                timerActive = false
            )
        )
        assertFalse(
            shouldLockOnBackground(
                appLockEnabled = true,
                credentialPresent = false,
                backgroundAutoLockEnabled = true,
                mode = BackgroundLockMode.ALWAYS,
                timerActive = false
            )
        )
        assertFalse(
            shouldLockOnBackground(
                appLockEnabled = true,
                credentialPresent = true,
                backgroundAutoLockEnabled = false,
                mode = BackgroundLockMode.ALWAYS,
                timerActive = false
            )
        )
    }
}
