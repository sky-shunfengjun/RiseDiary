package com.risediary.app.ui

import com.risediary.app.data.SecuritySettingsSnapshot
import org.junit.Assert.*
import org.junit.Test

class AppGateSecurityTest {
    @Test fun unreadableEnabledCredentialDoesNotBecomeOnboardingOrMain() {
        for (credential in listOf("", "abc", "hmac:bad")) {
            assertEquals(AppGateState.ERROR, resolveAppGate(SecuritySettingsSnapshot(lockEnabled = true, credential = credential)))
        }
    }
    @Test fun interruptedOnboardingStillRequiresTheExistingLock() {
        val snapshot = SecuritySettingsSnapshot(onboardingCompleted = true, lockEnabled = true, credential = "1234")
        assertEquals(AppGateState.LOCKED, resolveAppGate(snapshot))
        assertEquals(AppGateState.LOCKED, resolveAppGate(snapshot, "5678"))
        assertEquals(AppGateState.MAIN, resolveAppGate(snapshot, "1234"))
    }
    @Test fun hmacCredentialProofIsBoundToTheCurrentCredential() {
        val credential = "hmac:" + java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        val snapshot = SecuritySettingsSnapshot(true, true, credential)
        assertEquals(AppGateState.LOCKED, resolveAppGate(snapshot))
        assertEquals(AppGateState.MAIN, resolveAppGate(snapshot, credential))
        assertEquals(AppGateState.LOCKED, resolveAppGate(snapshot.copy(credential = "1234"), credential))
    }
    @Test fun firstInstallWithoutLockUsesOnboarding() {
        assertEquals(AppGateState.ONBOARDING, resolveAppGate(SecuritySettingsSnapshot()))
        assertEquals(AppGateState.MAIN, resolveAppGate(SecuritySettingsSnapshot(onboardingCompleted = true)))
    }
}