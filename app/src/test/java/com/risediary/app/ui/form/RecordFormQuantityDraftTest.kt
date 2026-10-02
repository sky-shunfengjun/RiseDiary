package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFormQuantityDraftTest {
    @Test
    fun unitSwitchDoesNotChangeAuthoritativeInputOrLoseDecimals() {
        val draft = RecordFormQuantityDraft()
        draft.enter(RecordVolumeMode.MILLILITERS, "1.55")
        assertEquals("1", draft.display(RecordVolumeMode.SPURTS, 2f))
        assertEquals("1.55", draft.display(RecordVolumeMode.MILLILITERS, 2f))
        val values = draft.resolve(2f).getOrThrow()
        assertEquals(1.55f, values.semenVolumeMl!!, 0.001f)
        assertEquals(RecordVolumeMode.MILLILITERS, draft.inputModeForSave)
    }

    @Test
    fun explicitEditAfterSwitchReplacesTheOldMlSource() {
        val draft = RecordFormQuantityDraft()
        draft.enter(RecordVolumeMode.MILLILITERS, "6")
        draft.display(RecordVolumeMode.SPURTS, 2f)
        draft.enter(RecordVolumeMode.SPURTS, "10")
        val values = draft.resolve(2f).getOrThrow()
        assertEquals(10, values.spurtCount)
        assertEquals(20f, values.semenVolumeMl!!, 0.001f)
        assertEquals(RecordVolumeMode.SPURTS, draft.inputModeForSave)
    }

    @Test
    fun untouchedHistoryPreservesBothStoredNumbersAndOriginalSource() {
        val draft = RecordFormQuantityDraft(history())
        draft.display(RecordVolumeMode.SPURTS, 4f)
        draft.display(RecordVolumeMode.MILLILITERS, 4f)
        val values = draft.resolve(4f).getOrThrow()
        assertEquals(3, values.spurtCount)
        assertEquals(1.5f, values.semenVolumeMl!!, 0.001f)
        assertEquals(RecordVolumeMode.MILLILITERS, draft.inputModeForSave)
    }

    @Test
    fun explicitlyEditedHistoryUsesCurrentConversion() {
        val draft = RecordFormQuantityDraft(history())
        draft.enter(RecordVolumeMode.SPURTS, "5")
        val values = draft.resolve(4f).getOrThrow()
        assertEquals(5, values.spurtCount)
        assertEquals(20f, values.semenVolumeMl!!, 0.001f)
    }

    @Test
    fun historyWithOneMissingUnitKeepsThatFieldMissing() {
        val draft = RecordFormQuantityDraft(history().copy(spurtCount = null))
        draft.display(RecordVolumeMode.SPURTS, 4f)
        assertEquals(null, draft.resolve(4f).getOrThrow().spurtCount)
    }

    private fun history() = Flight(
        id = 1, startTime = 1_000, endTime = 61_000, durationSeconds = 60,
        spurtCount = 3, semenVolumeMl = 1.5f,
        volumeInputMode = RecordVolumeMode.MILLILITERS.storedValue,
        ejaculationDistanceCm = null, methodTags = "[]", moodNote = "legacy",
        createdAt = 1_000, updatedAt = 61_000
    )
}