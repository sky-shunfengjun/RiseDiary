package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.util.RecordQuantityPolicy
import com.risediary.app.util.RecordQuantityValues

/** Display switches never turn a rounded, derived number into the original input. */
internal class RecordFormQuantityDraft(private val original: Flight? = null) {
    private var sourceMode = original?.let { RecordVolumeMode.fromStoredValue(it.volumeInputMode) }
        ?: RecordVolumeMode.MILLILITERS
    private var sourceText = ""
    private var quantityWasEdited = false

    val inputModeForSave: RecordVolumeMode
        get() = sourceMode

    fun enter(mode: RecordVolumeMode, value: String) {
        sourceMode = mode
        sourceText = value
        quantityWasEdited = true
    }

    fun resolve(mlPerSpurt: Float): Result<RecordQuantityValues> =
        if (original != null && !quantityWasEdited) {
            Result.success(RecordQuantityValues(original.spurtCount, original.semenVolumeMl))
        } else {
            RecordQuantityPolicy.calculate(sourceMode, sourceText, mlPerSpurt)
        }

    fun display(mode: RecordVolumeMode, mlPerSpurt: Float): String {
        if (quantityWasEdited && mode == sourceMode) return sourceText
        val values = resolve(mlPerSpurt).getOrNull() ?: return ""
        return when (mode) {
            RecordVolumeMode.SPURTS -> values.spurtCount?.toString()
                ?: values.semenVolumeMl?.let { (it / mlPerSpurt).toInt().coerceAtLeast(1).toString() }
                .orEmpty()
            RecordVolumeMode.MILLILITERS -> values.semenVolumeMl?.toString()
                ?: values.spurtCount?.let { (it * mlPerSpurt).toString() }.orEmpty()
        }
    }
}