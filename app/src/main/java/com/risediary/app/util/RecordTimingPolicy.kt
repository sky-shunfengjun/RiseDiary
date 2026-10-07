package com.risediary.app.util

import kotlin.math.abs

object RecordTimingPolicy {
    fun validate(startTime: Long, endTime: Long, durationSeconds: Int, timingSource: String, allowLegacyDuration: Boolean = false): String? {
        if (startTime <= 0L || endTime < startTime) return "记录时间无效"
        if (durationSeconds !in 1..(if (allowLegacyDuration) RecordValidation.LEGACY_MAX_DURATION_SECONDS else DurationPolicy.MAX_SECONDS)) return "用时超出范围"
        val spanSeconds = (endTime - startTime) / 1_000L
        return when (timingSource) {
            "manual" -> if (abs(spanSeconds - durationSeconds.toLong()) <= 1L) null else "时间与用时不一致"
            "timer" -> if (spanSeconds + 1L >= durationSeconds.toLong()) null else "计时用时超过实际时间"
            else -> "时间来源无效"
        }
    }
}
