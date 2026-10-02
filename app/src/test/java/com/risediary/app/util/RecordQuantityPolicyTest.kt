package com.risediary.app.util

import com.risediary.app.data.entity.RecordVolumeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordQuantityPolicyTest {
    @Test
    fun tenSpurtsUseLoadedConversion() {
        val values = RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, "10", 3f).getOrThrow()
        assertEquals(10, values.spurtCount)
        assertEquals(30f, values.semenVolumeMl!!, 0.001f)
    }

    @Test
    fun changedConversionAffectsOnlyNewCalculation() {
        val first = RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, "10", 3f).getOrThrow()
        val second = RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, "10", 4f).getOrThrow()
        assertEquals(30f, first.semenVolumeMl!!, 0.001f)
        assertEquals(40f, second.semenVolumeMl!!, 0.001f)
    }

    @Test
    fun legalDerivedValuesCanExceedTheOtherUnitsDirectInputLimit() {
        val maximumSpurts = RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, "1000", 100f).getOrThrow()
        val maximumMl = RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "1000", 0.1f).getOrThrow()
        assertEquals(100_000f, maximumSpurts.semenVolumeMl!!, 0.001f)
        assertEquals(10_000, maximumMl.spurtCount)
    }

    @Test
    fun volumeToSpurtsRoundsDownButNeverToZero() {
        assertEquals(1, RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "0.1", 100f).getOrThrow().spurtCount)
        assertEquals(3, RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, "7.99", 2f).getOrThrow().spurtCount)
    }

    @Test
    fun rejectsInvalidDirectInputEvenIfConvertedValueWouldBeSafe() {
        listOf("", "0", "-1", "1001", "1.5").forEach {
            assertTrue(RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, it, 2f).isFailure)
        }
        listOf("", "0", "0.09", "1000.1", "NaN", "Infinity").forEach {
            assertTrue(RecordQuantityPolicy.calculate(RecordVolumeMode.MILLILITERS, it, 2f).isFailure)
        }
    }

    @Test
    fun refusesUnloadedOrInvalidConversionInsteadOfReplacingItWithDefault() {
        listOf(0f, -1f, 0.09f, 100.1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertTrue(RecordQuantityPolicy.calculate(RecordVolumeMode.SPURTS, "10", it).isFailure)
        }
    }
}