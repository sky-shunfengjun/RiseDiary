package com.risediary.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricAuthenticatorTest {
    @Test
    fun availabilityCheckReturnsFalseWhenSystemServiceThrows() {
        assertFalse(
            safeBiometricAvailability {
                throw IllegalStateException("biometric service unavailable")
            }
        )
    }

    @Test
    fun availabilityCheckPreservesSuccessfulResult() {
        assertTrue(safeBiometricAvailability { true })
        assertFalse(safeBiometricAvailability { false })
    }
}
