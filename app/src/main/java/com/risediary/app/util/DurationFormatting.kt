package com.risediary.app.util

fun formatFormDuration(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    return "${safeSeconds / 60}分%02d秒".format(safeSeconds % 60)
}

fun formatNaturalDuration(totalSeconds: Int, includeSeconds: Boolean = true): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = (safeSeconds % 3_600) / 60
    val seconds = safeSeconds % 60
    if (hours == 0) {
        return if (includeSeconds) {
            "${minutes}分${seconds}秒"
        } else {
            "${minutes}分"
        }
    }
    return buildString {
        append(hours)
        append("小时")
        if (minutes > 0 || (includeSeconds && seconds > 0)) {
            append(minutes)
            append("分")
        }
        if (includeSeconds && seconds > 0) {
            append(seconds)
            append("秒")
        }
    }
}

fun formatTimerClock(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
