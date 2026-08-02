package com.risediary.app.util

object RecordValidation {
    const val MAX_DURATION_SECONDS = 120 * 60
    const val LEGACY_MAX_DURATION_SECONDS = 24 * 60 * 60

    fun validate(
        durationSeconds: Int,
        spurtCount: Int?,
        volumeMl: Float?,
        distanceCm: Float?,
        distanceWasEntered: Boolean,
        allowLegacyDuration: Boolean = false
    ): String? {
        val durationLimit = if (allowLegacyDuration) {
            LEGACY_MAX_DURATION_SECONDS
        } else {
            MAX_DURATION_SECONDS
        }
        if (durationSeconds !in 1..durationLimit) {
            return "起飞用时需要在 1 秒到 120 分钟之间"
        }
        if (spurtCount == null && volumeMl == null) {
            return "请填写射精量（股数或毫升）"
        }
        if (spurtCount != null && spurtCount !in 1..1_000) {
            return "股数需要在 1 到 1000 之间"
        }
        if (volumeMl != null && volumeMl !in 0.1f..1_000f) {
            return "毫升数需要在 0.1 到 1000 之间"
        }
        if (distanceWasEntered && (distanceCm == null || distanceCm !in 0f..1_000f)) {
            return "距离需要在 0 到 1000 厘米之间"
        }
        return null
    }
}
