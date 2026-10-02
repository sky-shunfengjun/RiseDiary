package com.risediary.app.update

import java.io.IOException
import java.time.Instant

@kotlinx.serialization.Serializable
enum class ReleaseChannel { STABLE, PREVIEW }
internal data class UpdateCheckPolicy(val force: Boolean, val channel: ReleaseChannel)
internal fun UpdateSettings.checkPolicy() = UpdateCheckPolicy(forceCheck, releaseChannel)
internal fun isReleaseAvailable(current: String, release: GitHubRelease, policy: UpdateCheckPolicy): Boolean {
    check(isVersionRecognized(current) && isVersionRecognized(release.tagName)) { "Invalid version" }
    return policy.force || isNewerVersion(current, release.tagName)
}

internal fun newestPublishedRelease(releases: List<GitHubRelease>): GitHubRelease =
    releases.map { release ->
        val time = try { Instant.parse(release.publishedAt ?: throw IOException("Missing publication time")) }
        catch (error: Exception) { throw IOException("Invalid publication time", error) }
        release to time
    }.maxWithOrNull(compareBy<Pair<GitHubRelease, Instant>> { it.second }.thenBy { it.first.id })?.first
        ?: throw IOException("No published release")