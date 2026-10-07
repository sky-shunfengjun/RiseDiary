package com.risediary.app.util

import com.risediary.app.data.entity.RecordVolumeMode
import org.junit.Assert.*
import org.junit.Test

class RecordQuantityPolicyTest {
    @Test fun manualQuantityDoesNotInventSpurtCount() {
        val values = RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "9.5").getOrThrow()
        assertNull(values.spurtCount)
        assertEquals(9.5f, values.semenVolumeMl!!, 0.001f)
    }
    @Test fun estimatedQuantityStoresOnlyMilliliters() {
        val values = RecordQuantityPolicy.calculate(RecordVolumeMode.ESTIMATED, "2.3").getOrThrow()
        assertNull(values.spurtCount)
        assertEquals(2.3f, values.semenVolumeMl!!, 0.001f)
    }
    @Test fun newSpurtInputIsRejected() {
        assertTrue(RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, "3").isFailure)
    }
    @Test fun invalidQuantitiesAreRejectedInBothNewModes() {
        listOf(RecordVolumeMode.MILLILITERS, RecordVolumeMode.ESTIMATED).forEach { mode ->
            listOf("", "0", "-1", "0.09", "1000.1", "NaN", "Infinity").forEach {
                assertTrue("$mode: $it", RecordQuantityPolicy.calculate(mode, it).isFailure)
            }
        }
    }
    @Test fun manualInputKeepsItsDecimalsAndAcceptsDirectBoundaries() {
        assertEquals(1.55f, RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "1.55").getOrThrow().semenVolumeMl!!, 0f)
        assertTrue(RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "0.1").isSuccess)
        assertTrue(RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "1000").isSuccess)
    }
}
