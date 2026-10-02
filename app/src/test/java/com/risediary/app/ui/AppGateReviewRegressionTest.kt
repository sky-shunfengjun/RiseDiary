package com.risediary.app.ui

import com.risediary.app.data.SecuritySettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/** The onboarding marker is not authorization to skip an already configured PIN. */
class AppGateReviewRegressionTest {
    @Test
    fun existingLockMustBeVerifiedBeforeIncompleteOnboarding() {
        val snapshot = SecuritySettingsSnapshot(
            onboardingCompleted = false,
            lockEnabled = true,
            credential = "1234"
        )
        assertEquals(AppGateState.LOCKED, resolveAppGate(snapshot))
        assertEquals(AppGateState.LOCKED, resolveAppGate(snapshot, "5678"))
        assertEquals(AppGateState.ONBOARDING, resolveAppGate(snapshot, "1234"))
    }
}