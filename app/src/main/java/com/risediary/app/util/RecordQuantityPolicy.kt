package com.risediary.app.util

import com.risediary.app.data.entity.RecordVolumeMode

/** Nullable legacy fields remain nullable; new input never calculates a spurt count. */
data class RecordQuantityValues(val spurtCount: Int?, val semenVolumeMl: Float?)

object RecordQuantityPolicy {
    fun calculate(mode: RecordVolumeMode, entered: String): Result<RecordQuantityValues> = runCatching {
        require(mode != RecordVolumeMode.SPURTS) { "新记录请使用预测或毫升输入" }
        val volume = entered.toFloatOrNull()
        require(volume != null && volume.isFinite() && volume in 0.1f..RecordValidation.MAX_DIRECT_VOLUME_ML) {
            "请填写射精量，毫升数需要在 0.1 到 1000 之间"
        }
        RecordQuantityValues(null, volume)
    }
}
