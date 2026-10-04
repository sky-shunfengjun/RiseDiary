package com.risediary.app.ui.video

import java.util.Locale

/** Unknown durations and transient restored positions must never become invalid seeks. */
internal fun videoProgressFraction(positionMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0L) return 0f
    return (positionMillis.coerceIn(0L, durationMillis).toDouble() / durationMillis).toFloat()
}

internal fun videoSeekPosition(fraction: Float, durationMillis: Long): Long? {
    if (durationMillis <= 0L || !fraction.isFinite()) return null
    val safeFraction = fraction.coerceIn(0f, 1f)
    if (safeFraction == 1f) return durationMillis
    return (safeFraction.toDouble() * durationMillis.toDouble()).toLong()
        .coerceIn(0L, durationMillis)
}

internal fun formatVideoPosition(positionMillis: Long?): String {
    if (positionMillis == null) return "--:--"
    val seconds = positionMillis.coerceAtLeast(0L) / 1_000L
    return if (seconds >= 3_600L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L)
    }
}
