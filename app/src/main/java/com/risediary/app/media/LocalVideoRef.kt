package com.risediary.app.media

import com.risediary.app.data.entity.Flight
import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class LocalVideoRef(val uriString: String, val displayName: String, val mimeType: String? = null)

@Serializable
data class VideoPlaybackSnapshot(
    val video: LocalVideoRef,
    val positionMillis: Long = 0L,
    val speed: Float = 1f,
    val loop: Boolean = false
)

enum class VideoAccessState { READABLE, MISSING, PERMISSION_LOST, INVALID }

fun isSupportedLocalVideoUri(value: String): Boolean = value.length <= 8_192 && runCatching {
    val uri = URI(value)
    uri.scheme == "content" && !uri.rawAuthority.isNullOrBlank() &&
        uri.rawUserInfo == null && !uri.rawPath.isNullOrBlank()
}.getOrDefault(false)

fun validateLocalVideoFields(uri: String?, name: String?, mime: String?): String? {
    if (uri == null && name == null && mime == null) return null
    if (uri == null || !isSupportedLocalVideoUri(uri)) return "视频关联无效，请重新选择"
    if (name.isNullOrBlank() || name.length > 1_024) return "视频名称无效"
    if (mime != null && (mime.length > 255 || !mime.startsWith("video/", ignoreCase = true))) {
        return "视频类型无效"
    }
    return null
}

fun Flight.localVideoRef(): LocalVideoRef? = videoUri?.let {
    LocalVideoRef(it, videoDisplayName?.takeIf(String::isNotBlank) ?: "本地视频", videoMimeType)
}