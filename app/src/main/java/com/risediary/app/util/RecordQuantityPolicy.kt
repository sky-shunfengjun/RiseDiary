package com.risediary.app.util

import com.risediary.app.data.entity.RecordVolumeMode

/** Both fields are nullable to preserve old records that contain only one unit. */
data class RecordQuantityValues(val spurtCount: Int?, val semenVolumeMl: Float?)

object RecordQuantityPolicy {
    fun calculate(mode: RecordVolumeMode, entered: String, mlPerSpurt: Float): Result<RecordQuantityValues> {
        if (!mlPerSpurt.isFinite() || mlPerSpurt !in 0.1f..100f) {
            return Result.failure(IllegalArgumentException("每股毫升设置无效，请重新读取设置"))
        }
        val values = when (mode) {
            RecordVolumeMode.SPURTS -> {
                val count = entered.toIntOrNull()
                if (count == null || count !in 1..RecordValidation.MAX_DIRECT_SPURTS) {
                    return Result.failure(IllegalArgumentException("股数需要在 1 到 1000 之间"))
                }
                RecordQuantityValues(count, count * mlPerSpurt)
            }
            RecordVolumeMode.MILLILITERS -> {
                val volume = entered.toFloatOrNull()
                if (volume == null || !volume.isFinite() || volume !in 0.1f..RecordValidation.MAX_DIRECT_VOLUME_ML) {
                    return Result.failure(IllegalArgumentException("毫升数需要在 0.1 到 1000 之间"))
                }
                RecordQuantityValues((volume / mlPerSpurt).toInt().coerceAtLeast(1), volume)
            }
        }
        val error = RecordValidation.validateStoredQuantity(values.spurtCount, values.semenVolumeMl)
        return if (error == null) Result.success(values) else Result.failure(IllegalArgumentException(error))
    }
}