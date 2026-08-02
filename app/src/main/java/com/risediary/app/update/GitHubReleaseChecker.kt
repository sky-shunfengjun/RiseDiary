package com.risediary.app.update

import android.net.Uri
import com.risediary.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val releaseUrl: String
)

/** Reads only public release metadata; no record or account data is sent. */
class GitHubReleaseChecker @javax.inject.Inject constructor() {

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "RiseDiary/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP ${connection.responseCode}")
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = JSONObject(json)
            if (payload.optBoolean("draft") || payload.optBoolean("prerelease")) return@withContext null

            val tagName = payload.optString("tag_name").trim()
            if (tagName.isBlank()) return@withContext null

            val releaseUrl = safeReleaseUrl(payload.optString("html_url")) ?: RELEASES_URL
            GitHubRelease(
                tagName = tagName,
                name = payload.optString("name").trim().ifBlank { tagName },
                releaseUrl = releaseUrl
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun safeReleaseUrl(value: String): String? {
        val uri = Uri.parse(value.trim())
        return value.takeIf {
            uri.scheme == "https" && uri.host == "github.com" &&
                uri.path?.startsWith("/sky-shunfengjun/RiseDiary/releases") == true
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/sky-shunfengjun/RiseDiary/releases/latest"
        const val RELEASES_URL = "https://github.com/sky-shunfengjun/RiseDiary/releases"
    }
}

private data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>?
)

private val VERSION_PATTERN =
    Regex("^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?$")

/** Returns true only when latest is a parseable semantic version newer than current. */
internal fun isNewerVersion(current: String, latest: String): Boolean {
    val currentVersion = parseVersion(current) ?: return false
    val latestVersion = parseVersion(latest) ?: return false
    return compareVersions(latestVersion, currentVersion) > 0
}

private fun parseVersion(value: String): ParsedVersion? {
    val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
    fun number(group: Int): Int =
        match.groupValues[group].toLongOrNull()
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 0

    return ParsedVersion(
        major = number(1),
        minor = number(2),
        patch = number(3),
        prerelease = match.groupValues[4].takeIf { it.isNotBlank() }?.split('.')
    )
}

private fun compareVersions(left: ParsedVersion, right: ParsedVersion): Int {
    compareValues(left.major, right.major).takeIf { it != 0 }?.let { return it }
    compareValues(left.minor, right.minor).takeIf { it != 0 }?.let { return it }
    compareValues(left.patch, right.patch).takeIf { it != 0 }?.let { return it }

    val leftPre = left.prerelease
    val rightPre = right.prerelease
    if (leftPre == null && rightPre == null) return 0
    if (leftPre == null) return 1
    if (rightPre == null) return -1

    for (index in 0 until maxOf(leftPre.size, rightPre.size)) {
        val leftPart = leftPre.getOrNull(index) ?: return -1
        val rightPart = rightPre.getOrNull(index) ?: return 1
        if (leftPart == rightPart) continue
        val leftNumber = leftPart.toLongOrNull()
        val rightNumber = rightPart.toLongOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> leftPart.compareTo(rightPart)
        }
    }
    return 0
}
