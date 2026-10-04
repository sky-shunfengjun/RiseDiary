package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.util.PredictionQuantitySettings
import com.risediary.app.util.RecordQuantityPolicy
import com.risediary.app.util.RecordQuantityValues
import kotlin.math.roundToInt

@kotlinx.serialization.Serializable
data class QuantityDraftSnapshot(
    val selectedMode: String,
    val estimatedTicks: Int,
    val manualText: String,
    val predictionMaxTicks: Int,
    val quantityWasEdited: Boolean
)

/** Each mode owns its input; saved historical quantities are never recalculated. */
internal class RecordFormQuantityDraft(
    private val original: Flight? = null,
    predictionMaxTicks: Int = PredictionQuantitySettings.DEFAULT_MAX_TICKS
) {
    private var selectedMode = original?.let { RecordVolumeMode.fromStoredValue(it.volumeInputMode) }
        ?.let { if (it == RecordVolumeMode.SPURTS) RecordVolumeMode.MILLILITERS else it }
        ?: RecordVolumeMode.ESTIMATED
    private var maxTicks = PredictionQuantitySettings.requireMaximum(
        original?.predictionMaxTicks ?: predictionMaxTicks
    )
    private var estimatedTicks = if (original?.volumeInputMode == RecordVolumeMode.ESTIMATED.storedValue)
        ((original.semenVolumeMl ?: 0f) * 10).roundToInt().also { require(it in 1..maxTicks) }
    else 0
    private var manualText = if (selectedMode == RecordVolumeMode.MILLILITERS)
        original?.semenVolumeMl?.toString().orEmpty() else ""
    private var quantityWasEdited = false
    private val isLegacyOriginal = original != null && (
        original.spurtCount != null || original.volumeInputMode == RecordVolumeMode.SPURTS.storedValue ||
            (original.legacyVolumeInputMode != null &&
                original.legacyVolumeInputMode == original.volumeInputMode &&
                original.legacySpurtCount == original.spurtCount && original.legacyVolumeMl == original.semenVolumeMl)
        )

    val isLegacyQuantityReadOnly: Boolean get() = isLegacyOriginal && !quantityWasEdited
    val inputModeForSave: RecordVolumeMode get() = if (original != null && !quantityWasEdited)
        RecordVolumeMode.fromStoredValue(original.volumeInputMode) else selectedMode

    fun selectMode(mode: RecordVolumeMode) {
        require(mode != RecordVolumeMode.SPURTS && !isLegacyQuantityReadOnly)
        if (mode != selectedMode) quantityWasEdited = true
        selectedMode = mode
    }

    fun setEstimatedTicks(ticks: Int) {
        require(ticks in 0..maxTicks) { "预测数量超出当前滑块范围" }
        require(!isLegacyQuantityReadOnly)
        estimatedTicks = ticks
        quantityWasEdited = true
    }

    fun setManualText(text: String) {
        require(!isLegacyQuantityReadOnly)
        manualText = text
        quantityWasEdited = true
    }

    fun beginLegacyQuantityEdit() {
        if (!isLegacyQuantityReadOnly) return
        selectedMode = RecordVolumeMode.MILLILITERS
        estimatedTicks = 0
        manualText = original?.semenVolumeMl?.toString().orEmpty()
        quantityWasEdited = true
    }

    fun resolve(): Result<RecordQuantityValues> = if (original != null && !quantityWasEdited) {
        Result.success(RecordQuantityValues(original.spurtCount, original.semenVolumeMl))
    } else {
        RecordQuantityPolicy.calculate(selectedMode, if (selectedMode == RecordVolumeMode.ESTIMATED)
            PredictionQuantitySettings.formatTicks(estimatedTicks) else manualText)
    }

    fun applyTo(flight: Flight): Flight {
        val values = resolve().getOrThrow()
        val needsLegacySnapshot = original != null && isLegacyOriginal && original.legacyVolumeInputMode == null
        return flight.copy(
            spurtCount = values.spurtCount,
            semenVolumeMl = values.semenVolumeMl,
            volumeInputMode = inputModeForSave.storedValue,
            legacySpurtCount = if (needsLegacySnapshot) original.spurtCount else original?.legacySpurtCount,
            legacyVolumeMl = if (needsLegacySnapshot) original.semenVolumeMl else original?.legacyVolumeMl,
            legacyVolumeInputMode = if (needsLegacySnapshot) original.volumeInputMode else original?.legacyVolumeInputMode,
            predictionMaxTicks = if (inputModeForSave == RecordVolumeMode.ESTIMATED) maxTicks else null
        )
    }

    fun snapshot() = QuantityDraftSnapshot(selectedMode.storedValue, estimatedTicks, manualText, maxTicks, quantityWasEdited)

    companion object {
        fun restore(snapshot: QuantityDraftSnapshot, original: Flight? = null): RecordFormQuantityDraft {
            val mode = RecordVolumeMode.entries.single { it.storedValue == snapshot.selectedMode }
            require(mode != RecordVolumeMode.SPURTS)
            require(snapshot.estimatedTicks in 0..PredictionQuantitySettings.requireMaximum(snapshot.predictionMaxTicks))
            return RecordFormQuantityDraft(original, snapshot.predictionMaxTicks).apply {
                selectedMode = mode
                maxTicks = snapshot.predictionMaxTicks
                estimatedTicks = snapshot.estimatedTicks
                manualText = snapshot.manualText
                quantityWasEdited = snapshot.quantityWasEdited
            }
        }
    }
}
