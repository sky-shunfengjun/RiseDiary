package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import org.junit.Assert.*
import org.junit.Test

class RecordFormQuantityDraftTest {
    @Test fun newRecordStartsAtZeroInPredictionModeAndCannotSave() {
        val draft = RecordFormQuantityDraft()
        assertEquals(RecordVolumeMode.ESTIMATED, draft.inputModeForSave)
        assertEquals(0, draft.snapshot().estimatedTicks)
        assertEquals(80, draft.snapshot().predictionMaxTicks)
        assertTrue(draft.resolve().isFailure)
    }
    @Test fun switchingModesPreservesSeparateInputsIncludingManualValueAboveSliderMaximum() {
        val draft = RecordFormQuantityDraft()
        draft.setEstimatedTicks(23)
        draft.selectMode(RecordVolumeMode.MILLILITERS)
        assertEquals("", draft.snapshot().manualText)
        draft.setManualText("9.5")
        assertEquals(9.5f, draft.resolve().getOrThrow().semenVolumeMl!!, 0f)
        draft.selectMode(RecordVolumeMode.ESTIMATED)
        assertEquals(2.3f, draft.resolve().getOrThrow().semenVolumeMl!!, 0f)
        draft.selectMode(RecordVolumeMode.MILLILITERS)
        assertEquals("9.5", draft.snapshot().manualText)
        assertNull(draft.resolve().getOrThrow().spurtCount)
    }
    @Test fun snapshotRetainsBothInputsAndItsOwnRange() {
        val draft = RecordFormQuantityDraft(predictionMaxTicks = 200)
        draft.setEstimatedTicks(155)
        draft.selectMode(RecordVolumeMode.MILLILITERS)
        draft.setManualText("22.55")
        val restored = RecordFormQuantityDraft.restore(draft.snapshot())
        assertEquals(draft.snapshot(), restored.snapshot())
        assertEquals(22.55f, restored.resolve().getOrThrow().semenVolumeMl!!, 0f)
        restored.selectMode(RecordVolumeMode.ESTIMATED)
        assertEquals(15.5f, restored.resolve().getOrThrow().semenVolumeMl!!, 0f)
    }
    @Test fun untouchedOldRecordKeepsMissingMillilitersAndOriginalMode() {
        val old = history().copy(semenVolumeMl = null, volumeInputMode = "spurts")
        val draft = RecordFormQuantityDraft(old)
        assertTrue(draft.isLegacyQuantityReadOnly)
        assertEquals(3, draft.resolve().getOrThrow().spurtCount)
        assertNull(draft.resolve().getOrThrow().semenVolumeMl)
        assertEquals(RecordVolumeMode.SPURTS, draft.inputModeForSave)
    }
    @Test fun explicitLegacyEditRequiresAQuantityAndKeepsHistoryWhenApplied() {
        val old = history()
        val draft = RecordFormQuantityDraft(old)
        draft.beginLegacyQuantityEdit()
        assertFalse(draft.isLegacyQuantityReadOnly)
        assertEquals(RecordVolumeMode.MILLILITERS, draft.inputModeForSave)
        assertEquals("1.5", draft.snapshot().manualText)
        draft.selectMode(RecordVolumeMode.ESTIMATED)
        draft.setEstimatedTicks(23)
        val changed = draft.applyTo(old)
        assertNull(changed.spurtCount)
        assertEquals(2.3f, changed.semenVolumeMl!!, 0f)
        assertEquals(3, changed.legacySpurtCount)
        assertEquals(1.5f, changed.legacyVolumeMl!!, 0f)
        val next = RecordFormQuantityDraft(changed)
        next.selectMode(RecordVolumeMode.MILLILITERS)
        next.setManualText("9.5")
        val editedAgain = next.applyTo(changed)
        assertEquals(1.5f, editedAgain.legacyVolumeMl!!, 0f)
        assertEquals(3, editedAgain.legacySpurtCount)
    }
    @Test fun editingPredictionKeepsRecordedMaximumEvenBelowNewMaximum() {
        val original = history().copy(spurtCount = null, semenVolumeMl = 2.3f,
            volumeInputMode = "estimated", legacySpurtCount = null,
            legacyVolumeMl = null, legacyVolumeInputMode = null, predictionMaxTicks = 200)
        val draft = RecordFormQuantityDraft(original, predictionMaxTicks = 80)
        assertEquals(200, draft.snapshot().predictionMaxTicks)
        assertEquals(23, draft.snapshot().estimatedTicks)
        assertEquals(original, draft.applyTo(original))
    }
    @Test fun editingOldSpurtModeDefaultsToStoredMillilitersAndPreservesHistory() {
        val old = history().copy(volumeInputMode = "spurts", legacyVolumeInputMode = "spurts")
        val draft = RecordFormQuantityDraft(old)
        assertEquals(old, draft.applyTo(old))
        draft.beginLegacyQuantityEdit()
        assertEquals(RecordVolumeMode.MILLILITERS, draft.inputModeForSave)
        assertEquals("1.5", draft.snapshot().manualText)
        val updated = draft.applyTo(old)
        assertNull(updated.spurtCount)
        assertEquals(1.5f, updated.semenVolumeMl!!, 0f)
        assertEquals(3, updated.legacySpurtCount)
        assertEquals("spurts", updated.legacyVolumeInputMode)
    }
    @Test fun editingSpurtsOnlyRequiresManualMillilitersWithoutInventingAConversion() {
        val old = history().copy(semenVolumeMl = null, volumeInputMode = "spurts", legacyVolumeMl = null, legacyVolumeInputMode = "spurts")
        val draft = RecordFormQuantityDraft(old)
        draft.beginLegacyQuantityEdit()
        assertEquals(RecordVolumeMode.MILLILITERS, draft.inputModeForSave)
        assertEquals("", draft.snapshot().manualText)
        assertTrue(draft.resolve().isFailure)
    }
    @Test fun legacyLargeQuantityCanBeSavedUnchangedButNotReentered() {
        val old = history().copy(semenVolumeMl = 100_000f)
        val draft = RecordFormQuantityDraft(old)
        assertEquals(100_000f, draft.resolve().getOrThrow().semenVolumeMl!!, 0f)
        draft.beginLegacyQuantityEdit()
        draft.selectMode(RecordVolumeMode.MILLILITERS)
        draft.setManualText("100000")
        assertTrue(draft.resolve().isFailure)
    }
    @Test fun sliderRejectsOutOfRangeValuesRatherThanClampingThem() {
        val draft = RecordFormQuantityDraft(predictionMaxTicks = 80)
        assertThrows(IllegalArgumentException::class.java) { draft.setEstimatedTicks(81) }
        assertThrows(IllegalArgumentException::class.java) { draft.setEstimatedTicks(-1) }
    }
    private fun history() = Flight(
        id = 1, startTime = 1_000, endTime = 61_000, durationSeconds = 60,
        spurtCount = 3, semenVolumeMl = 1.5f, volumeInputMode = "milliliters",
        ejaculationDistanceCm = null, methodTags = "[]", moodNote = "legacy",
        createdAt = 1_000, updatedAt = 61_000,
        legacySpurtCount = 3, legacyVolumeMl = 1.5f, legacyVolumeInputMode = "milliliters"
    )
}
