package com.risediary.app.data

import com.risediary.app.data.entity.RecordVolumeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultVolumeModeTest {

    @Test
    fun storedValuesRoundTrip() {
        DefaultVolumeMode.entries.forEach { mode ->
            assertEquals(mode, DefaultVolumeMode.fromStoredValue(mode.storedValue))
        }
    }

    @Test
    fun missingOrUnknownValueFallsBackToMilliliters() {
        assertEquals(
            DefaultVolumeMode.MILLILITERS,
            DefaultVolumeMode.fromStoredValue(null)
        )
        assertEquals(
            DefaultVolumeMode.MILLILITERS,
            DefaultVolumeMode.fromStoredValue("unknown")
        )
    }

    @Test
    fun recordVolumeModeStoredValuesRoundTrip() {
        RecordVolumeMode.entries.forEach { mode ->
            assertEquals(mode, RecordVolumeMode.fromStoredValue(mode.storedValue))
        }
        assertEquals(
            RecordVolumeMode.MILLILITERS,
            RecordVolumeMode.fromStoredValue("unknown")
        )
    }

    @Test
    fun legacyRecordModeOnlyInfersSpurtsWhenMlIsMissing() {
        assertEquals(RecordVolumeMode.SPURTS, RecordVolumeMode.inferLegacy(4, null))
        assertEquals(RecordVolumeMode.MILLILITERS, RecordVolumeMode.inferLegacy(4, 3.5f))
        assertEquals(RecordVolumeMode.MILLILITERS, RecordVolumeMode.inferLegacy(null, null))
    }
}
