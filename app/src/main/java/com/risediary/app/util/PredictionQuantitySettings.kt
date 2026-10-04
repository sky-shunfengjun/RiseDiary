package com.risediary.app.util

import java.math.BigDecimal

object PredictionQuantitySettings {
    const val DEFAULT_MAX_TICKS = 80
    // Retain the earlier development version's record/backup range.
    const val MAX_TICKS = 10_000
    const val MAX_SETTING_TICKS = 150

    fun requireMaximum(ticks: Int): Int {
        require(ticks in 1..MAX_TICKS) { "预测上限无效" }
        return ticks
    }

    fun requireSettingMaximum(ticks: Int): Int {
        require(ticks in 1..MAX_SETTING_TICKS) { "预测最大值需要在 0.1 到 15 毫升之间" }
        return ticks
    }

    fun normalizeStoredMaximum(ticks: Int): Int =
        requireMaximum(ticks).coerceAtMost(MAX_SETTING_TICKS)

    fun formatTicks(ticks: Int): String = BigDecimal.valueOf(ticks.toLong(), 1).toPlainString()
}
