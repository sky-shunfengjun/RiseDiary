package com.risediary.app.update

import com.risediary.app.BuildConfig
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

fun interface ReleaseSource {
    suspend fun fetchLatestRelease(channel: ReleaseChannel): GitHubRelease
}

internal data class ReleasePage(val releases: List<GitHubRelease>, val hasNext: Boolean)

/** Finish pagination before choosing: the API list order is not our publication policy. */
internal suspend fun fetchNewestPublishedRelease(fetchPage: suspend (Int) -> ReleasePage): GitHubRelease {
    val releases = mutableListOf<GitHubRelease>()
    var page = 1
    while (true) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val result = fetchPage(page)
        releases.addAll(result.releases)
        if (!result.hasNext) break
        if (page == Int.MAX_VALUE) throw IOException("Too many release pages")
        page++
    }
    return newestPublishedRelease(releases)
}

/** Only public release metadata is requested; diary data stays on the device. */
class GitHubReleaseChecker @Inject constructor() : ReleaseSource {
    override suspend fun fetchLatestRelease(channel: ReleaseChannel): GitHubRelease = withContext(Dispatchers.IO) {
        when (channel) {
            ReleaseChannel.STABLE -> parseReleaseResponse(request("/latest").first)
            ReleaseChannel.PREVIEW -> fetchNewestPublishedRelease { page ->
                val (json, next) = request("?per_page=100&page=$page")
                ReleasePage(parseReleaseList(json), next)
            }
        }
    }

    private fun request(suffix: String): Pair<String, Boolean> {
        val connection = (URL("https://api.github.com/repos/sky-shunfengjun/RiseDiary/releases$suffix").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "RiseDiary/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("GitHub HTTP ${connection.responseCode}")
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val content = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (content.length + count > 2_000_000) throw IOException("Release response too large")
                    content.append(buffer, 0, count)
                }
                content.toString()
            }
            // Only the existence of the next relation is used; every URL is built locally.
            val next = connection.getHeaderField("Link").orEmpty().split(',')
                .any { Regex("rel=\"next\"").containsMatchIn(it) }
            return json to next
        } finally { connection.disconnect() }
    }
}

internal fun parseReleaseResponse(json: String): GitHubRelease =
    parseRelease(Json.parseToJsonElement(json).jsonObject, stable = true)

internal fun parseReleaseList(json: String): List<GitHubRelease> =
    Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
        val payload = element.jsonObject
        when (payload["draft"]?.jsonPrimitive?.booleanOrNull) {
            true -> null
            false -> if (payload["published_at"] == JsonNull) null else parseRelease(payload, stable = false)
            else -> throw IOException("Invalid draft flag")
        }
    }

private fun parseRelease(payload: JsonObject, stable: Boolean): GitHubRelease {
    fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val prerelease = payload["prerelease"]?.jsonPrimitive?.booleanOrNull ?: throw IOException("Invalid release flag")
    if (payload["draft"]?.jsonPrimitive?.booleanOrNull != false || (stable && prerelease)) throw IOException("No stable release")
    val tag = payload.string("tag_name").trim()
    if (!isVersionRecognized(tag) || (stable && parseVersion(tag)?.prerelease != null)) throw IOException("Invalid release version")
    val assets = (payload["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
        val asset = element as? JsonObject ?: return@mapNotNull null
        val url = asset.string("browser_download_url").trim()
        if (asset.string("state") != "uploaded" || !isProjectApkUrl(url)) return@mapNotNull null
        val digest = asset.string("digest").removePrefix("sha256:").lowercase()
            .takeIf { asset.string("digest").startsWith("sha256:") && it.matches(Regex("[0-9a-f]{64}")) }
        GitHubAsset(asset["id"]?.jsonPrimitive?.longOrNull ?: 0L, asset.string("name"), url,
            asset["size"]?.jsonPrimitive?.longOrNull ?: 0L, digest)
    }
    val id = payload["id"]?.jsonPrimitive?.longOrNull ?: 0L
    val published = payload.string("published_at").takeIf { it.isNotBlank() }
    if (!stable) {
        if (id <= 0 || published == null) throw IOException("Invalid publication metadata")
        try { java.time.Instant.parse(published) } catch (error: Exception) { throw IOException("Invalid publication time", error) }
    }
    return GitHubRelease(tag, payload.string("name").ifBlank { tag },
        safeReleaseUrl(payload.string("html_url")) ?: "$RELEASES_URL/tag/$tag", payload.string("body"), assets,
        id = id, prerelease = prerelease, publishedAt = published)
}
internal fun isVersionRecognized(value: String): Boolean = parseVersion(value) != null

private data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>?
)

private val VERSION_PATTERN =
    Regex("^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-\\s]+([0-9A-Za-z][0-9A-Za-z._ -]*))?$")
private val PRERELEASE_PART_PATTERN = Regex("[A-Za-z]+|\\d+")

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
        prerelease = match.groupValues[4]
            .takeIf { it.isNotBlank() }
            ?.let { suffix ->
                PRERELEASE_PART_PATTERN.findAll(suffix)
                    .map { it.value.lowercase() }
                    .toList()
                    .takeIf(List<String>::isNotEmpty)
            }
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
