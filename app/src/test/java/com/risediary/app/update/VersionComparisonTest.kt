package com.risediary.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {

    @Test
    fun leadingVAndBetaAreCompared() {
        assertTrue(isNewerVersion("1.0.0-beta28", "v1.0.0-beta29"))
    }

    @Test
    fun stableReleaseIsNewerThanPrerelease() {
        assertTrue(isNewerVersion("1.0.0-beta28", "1.0.0"))
    }

    @Test
    fun developmentBuildNamesAreComparedNumerically() {
        assertTrue(isNewerVersion("v1.0.1 Dev3", "v1.0.1 Dev4"))
        assertTrue(isNewerVersion("v1.0.1 Dev9", "v1.0.1 Dev10"))
        assertFalse(isNewerVersion("v1.0.1 Dev10", "v1.0.1 Dev9"))
    }

    @Test
    fun stableReleaseIsNewerThanDevelopmentBuildOfSameVersion() {
        assertTrue(isNewerVersion("v1.0.1 Dev3", "v1.0.1"))
    }

    @Test
    fun betaBuildNumbersAreComparedNumerically() {
        assertTrue(isNewerVersion("1.0.0-beta9", "1.0.0-beta10"))
    }

    @Test
    fun sameOrOlderVersionDoesNotTriggerUpdate() {
        assertFalse(isNewerVersion("1.0.0-beta28", "1.0.0-beta28"))
        assertFalse(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun invalidLatestVersionIsIgnored() {
        assertFalse(isNewerVersion("1.0.0-beta28", "latest"))
    }
}
