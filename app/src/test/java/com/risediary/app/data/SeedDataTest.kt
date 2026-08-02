package com.risediary.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {
    @Test
    fun `fresh install seeds default tags`() {
        assertTrue(
            shouldSeedDefaultTags(
                initialized = false,
                onboardingCompleted = false,
                tagCount = 0
            )
        )
    }

    @Test
    fun `empty tag list is preserved after initialization`() {
        assertFalse(
            shouldSeedDefaultTags(
                initialized = true,
                onboardingCompleted = true,
                tagCount = 0
            )
        )
    }

    @Test
    fun `existing install without marker does not recreate deleted tags`() {
        assertFalse(
            shouldSeedDefaultTags(
                initialized = false,
                onboardingCompleted = true,
                tagCount = 0
            )
        )
    }
}
