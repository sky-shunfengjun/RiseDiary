package com.risediary.app.util

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import java.math.BigDecimal

object RecordQuantityDisplay {
    fun current(flight: Flight): String = format(flight.spurtCount, flight.semenVolumeMl,
        flight.volumeInputMode == RecordVolumeMode.ESTIMATED.storedValue, showOriginalSpurts = false)

    fun original(flight: Flight): String? = if (flight.legacyVolumeInputMode == null) null
        else format(flight.legacySpurtCount, flight.legacyVolumeMl, false)

    private fun format(spurts: Int?, volume: Float?, estimated: Boolean, showOriginalSpurts: Boolean = true): String {
        val ml = volume?.let { BigDecimal(it.toString()).stripTrailingZeros().toPlainString() + " 毫升" }
        return when {
            estimated && ml != null -> "约 $ml"
            !showOriginalSpurts && ml != null -> ml
            spurts != null -> "$spurts 股 · ${ml ?: "毫升未记录"}"
            ml != null -> ml
            else -> "数量未记录"
        }
    }
}
