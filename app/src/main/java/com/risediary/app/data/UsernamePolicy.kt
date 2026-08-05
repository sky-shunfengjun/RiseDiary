package com.risediary.app.data

object UsernamePolicy {
    const val MAX_LENGTH = 40
    const val DEFAULT_USERNAME = "机长"

    fun normalize(value: String): String =
        limit(value.trim()).ifBlank { DEFAULT_USERNAME }

    fun limit(value: String): String {
        if (isWithinLimit(value)) return value
        val endIndex = value.offsetByCodePoints(0, MAX_LENGTH)
        return value.substring(0, endIndex)
    }

    fun isWithinLimit(value: String): Boolean =
        value.codePointCount(0, value.length) <= MAX_LENGTH
}
