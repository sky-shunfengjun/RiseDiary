package com.risediary.app.ui.video

internal enum class VideoOrientation { PORTRAIT, LANDSCAPE }

internal data class VideoOrientationSession(
    val direction: VideoOrientation,
    val autoDecided: Boolean = false,
    val manual: Boolean = false
)

internal fun videoOrientation(width: Int, height: Int, pixelRatio: Float): VideoOrientation? {
    if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0f) return null
    val ratio = width.toDouble() * pixelRatio / height
    return when {
        kotlin.math.abs(ratio - 1.0) <= 0.001 -> null
        ratio > 1.0 -> VideoOrientation.LANDSCAPE
        else -> VideoOrientation.PORTRAIT
    }
}

internal fun beginVideoOrientation(
    width: Int, height: Int, pixelRatio: Float, fallback: VideoOrientation
): VideoOrientationSession {
    val resolved = videoOrientation(width, height, pixelRatio)
    return VideoOrientationSession(resolved ?: fallback, autoDecided = resolved != null)
}

internal fun resolveVideoOrientation(
    session: VideoOrientationSession, width: Int, height: Int, pixelRatio: Float
): VideoOrientationSession {
    if (session.manual || session.autoDecided) return session
    val resolved = videoOrientation(width, height, pixelRatio) ?: return session
    return session.copy(direction = resolved, autoDecided = true)
}

internal fun toggleVideoOrientation(session: VideoOrientationSession): VideoOrientationSession =
    session.copy(
        direction = if (session.direction == VideoOrientation.PORTRAIT) {
            VideoOrientation.LANDSCAPE
        } else {
            VideoOrientation.PORTRAIT
        },
        manual = true
    )
