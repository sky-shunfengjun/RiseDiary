package com.risediary.app.update

import kotlinx.serialization.Serializable
import java.net.URI

internal const val RELEASES_URL = "https://github.com/sky-shunfengjun/RiseDiary/releases"

@Serializable
data class GitHubRelease(
    val tagName: String,
    val name: String,
    val releaseUrl: String,
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList(),
    val id: Long = 0,
    val prerelease: Boolean = false,
    val publishedAt: String? = null
)

@Serializable
data class GitHubAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String? = null
)

@Serializable
enum class UpdateChannel {
    OFFICIAL, PROXY_7ED;
    fun downloadUrl(original: String): String {
        require(isProjectApkUrl(original)) { "Invalid APK address" }
        return when (this) {
            OFFICIAL -> original
            PROXY_7ED -> "https://gh.sevencdn.com/$original"
        }
    }
}

data class UpdateSettings(
    val automaticCheck: Boolean = true,
    val channel: UpdateChannel = UpdateChannel.OFFICIAL,
    val forceCheck: Boolean = false,
    val releaseChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val developerEnabled: Boolean = false
)

@Serializable
data class DownloadRecord(
    val id: Long,
    val release: GitHubRelease,
    val asset: GitHubAsset,
    val channel: UpdateChannel,
    val verificationFailed: Boolean = false
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Starting : DownloadState
    data class Running(val record: DownloadRecord, val percent: Int?) : DownloadState
    data class Paused(val record: DownloadRecord, val waitingForWifi: Boolean = false) : DownloadState
    data class Ready(val record: DownloadRecord) : DownloadState
    data class Verifying(val record: DownloadRecord) : DownloadState
    data class Failed(val record: DownloadRecord?, val reason: UpdateError) : DownloadState
}

enum class UpdateError {
    CHECK, SETTINGS, DOWNLOAD, STORAGE, FILE_MISSING, INTEGRITY, INSTALL, PERMISSION, LINK
}

internal fun DownloadState.recordOrNull(): DownloadRecord? = when (this) {
    is DownloadState.Running -> record
    is DownloadState.Paused -> record
    is DownloadState.Ready -> record
    is DownloadState.Verifying -> record
    is DownloadState.Failed -> record
    else -> null
}

internal fun DownloadState.isActive(): Boolean =
    this is DownloadState.Starting || this is DownloadState.Running ||
        this is DownloadState.Paused || this is DownloadState.Verifying

internal fun isProjectApkUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    val prefix = "/sky-shunfengjun/RiseDiary/releases/download/"
    val path = uri.path.orEmpty()
    uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
        uri.userInfo == null && uri.fragment == null && uri.query == null &&
        path.startsWith(prefix) && path.removePrefix(prefix).split('/').size == 2 &&
        path.endsWith(".apk", ignoreCase = true) && uri.normalize().path == path &&
        path.removePrefix(prefix).split('/').none { it in listOf(".", "..", "") } &&
        !path.contains('\\')
}.getOrDefault(false)

internal fun safeReleaseUrl(value: String): String? = runCatching {
    val uri = URI(value)
    value.takeIf {
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.userInfo == null && uri.fragment == null &&
            uri.path?.startsWith("/sky-shunfengjun/RiseDiary/releases/tag/") == true &&
            uri.normalize().path == uri.path
    }
}.getOrNull()

internal fun selectReleaseApk(release: GitHubRelease): GitHubAsset? {
    val candidates = release.assets.filter {
        it.id > 0 && it.size > 0 && it.name.endsWith(".apk", ignoreCase = true) &&
            !it.name.contains("androidtest", ignoreCase = true) &&
            !it.name.contains("debug", ignoreCase = true) && isProjectApkUrl(it.downloadUrl)
    }
    val named = candidates.filter { it.name.equals("RiseDiary-${release.tagName}.apk", ignoreCase = true) }
    return named.singleOrNull() ?: if (named.isEmpty()) candidates.singleOrNull() else null
}

internal fun downloadPercent(downloaded: Long, total: Long): Int? =
    if (total <= 0 || downloaded < 0) null
    else ((downloaded.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
