package com.risediary.app.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckPolicyTest {
    private fun release(tag: String, id: Long = 1, time: String = "2026-10-01T10:00:00Z", beta: Boolean = false) =
        GitHubRelease(tag, tag, RELEASES_URL, id = id, prerelease = beta, publishedAt = time)
    @Test fun forceOverridesComparisonForHigherEqualAndLowerVersions() {
        val normal = UpdateCheckPolicy(false, ReleaseChannel.STABLE)
        val forced = normal.copy(force = true)
        for ((tag, newer) in listOf("v2.0.0" to true, "v1.1.2" to false, "v1.0.0" to false)) {
            assertEquals(newer, isReleaseAvailable("v1.1.2", release(tag), normal))
            assertTrue(isReleaseAvailable("v1.1.2", release(tag), forced))
        }
    }
    @Test fun previewChoosesPublicationTimeRatherThanVersionAndUsesIdForTies() {
        val old = release("v99.0.0", 100, "2026-09-30T10:00:00Z")
        val newer = release("v1.0.0-beta1", 101, beta = true)
        val tie = newer.copy(id = 102, tagName = "v1.0.0-beta2")
        assertEquals(tie, newestPublishedRelease(listOf(old, newer, tie)))
        assertEquals(tie, newestPublishedRelease(listOf(tie, newer, old)))
    }
    @Test fun offsetTimesAreComparedAsInstants() {
        val earlier = release("v1.0.0", time = "2026-10-01T10:00:00+08:00")
        val later = release("v1.0.1", time = "2026-10-01T03:00:00Z")
        assertEquals(later, newestPublishedRelease(listOf(earlier, later)))
    }
    @Test fun emptyOrInvalidMetadataCannotBecomeUpToDate() {
        for (list in listOf(emptyList(), listOf(release("v1.0.0", time = "invalid")))) {
            assertTrue(runCatching { newestPublishedRelease(list) }.isFailure)
        }
    }
}