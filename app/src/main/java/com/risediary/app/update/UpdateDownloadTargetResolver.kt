package com.risediary.app.update

import kotlinx.serialization.Serializable

/** Evidence from a successful check; legacy records have no evidence and must be checked again to retry. */
@Serializable
data class DownloadAuthorization(
    val forceCheck: Boolean,
    val releaseChannel: ReleaseChannel,
    val currentVersion: String,
    val releaseTag: String,
    val assetId: Long
)

internal fun authorizeDownload(release: GitHubRelease, asset: GitHubAsset,
    settings: UpdateSettings, currentVersion: String) = DownloadAuthorization(
    settings.forceCheck, settings.releaseChannel, currentVersion, release.tagName, asset.id
)

internal sealed interface UpdateDownloadTarget {
    data class Authorized(val release: GitHubRelease, val asset: GitHubAsset,
        val authorization: DownloadAuthorization, val retryOriginal: Boolean = false) : UpdateDownloadTarget
    data class Blocked(val reason: UpdateError? = null, val checking: Boolean = false) : UpdateDownloadTarget
}

/** Buttons and enqueue share exactly the same successful-check and offline-retry rules. */
internal fun resolveUpdateDownloadTarget(ui: UpdateUiState): UpdateDownloadTarget {
    val settings = ui.settings
    fun valid(release: GitHubRelease, asset: GitHubAsset, proof: DownloadAuthorization?): Boolean =
        proof != null && proof.forceCheck == settings.forceCheck && proof.releaseChannel == settings.releaseChannel &&
            proof.releaseTag == release.tagName && proof.assetId == asset.id &&
            (settings.releaseChannel == ReleaseChannel.PREVIEW || !release.prerelease) &&
            runCatching { isReleaseAvailable(ui.currentVersion, release, settings.checkPolicy()) }.getOrDefault(false)

    return when (val check = ui.check) {
        UpdateCheckState.Checking -> UpdateDownloadTarget.Blocked(checking = true)
        is UpdateCheckState.Available -> {
            val asset = selectReleaseApk(check.release)
                ?: return UpdateDownloadTarget.Blocked(UpdateError.APK_UNAVAILABLE)
            val proof = ui.checkAuthorization
            if (valid(check.release, asset, proof)) UpdateDownloadTarget.Authorized(check.release, asset, proof!!)
            else UpdateDownloadTarget.Blocked(UpdateError.RECHECK_REQUIRED)
        }
        UpdateCheckState.Failed -> {
            val task = (ui.download as? DownloadState.Failed)?.record
                ?: return UpdateDownloadTarget.Blocked()
            val asset = selectReleaseApk(task.release)
            if (asset != null && asset == task.asset && valid(task.release, asset, task.authorization)) {
                UpdateDownloadTarget.Authorized(task.release, asset, task.authorization!!, retryOriginal = true)
            } else UpdateDownloadTarget.Blocked(UpdateError.RECHECK_REQUIRED)
        }
        UpdateCheckState.Idle, UpdateCheckState.UpToDate -> UpdateDownloadTarget.Blocked()
    }
}