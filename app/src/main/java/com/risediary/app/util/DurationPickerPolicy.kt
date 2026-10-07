package com.risediary.app.util

data class DurationParts(val hours: Int, val minutes: Int, val seconds: Int)

fun durationPartsFromSeconds(totalSeconds: Int): DurationParts {
    val value = totalSeconds.coerceIn(0, DurationPolicy.MAX_SECONDS)
    return DurationParts(value / 3_600, value % 3_600 / 60, value % 60)
}

fun durationSecondsFromParts(parts: DurationParts): Int {
    val maxHours = DurationPolicy.MAX_SECONDS / 3_600
    require(parts.hours in 0..maxHours && parts.minutes in 0..59 && parts.seconds in 0..59)
    require(parts.hours < maxHours || (parts.minutes == 0 && parts.seconds == 0))
    return parts.hours * 3_600 + parts.minutes * 60 + parts.seconds
}

fun durationPartsWithHours(parts: DurationParts, hours: Int): DurationParts {
    val maxHours = DurationPolicy.MAX_SECONDS / 3_600
    require(hours in 0..maxHours)
    return if (hours == maxHours) DurationParts(hours, 0, 0) else parts.copy(hours = hours)
}