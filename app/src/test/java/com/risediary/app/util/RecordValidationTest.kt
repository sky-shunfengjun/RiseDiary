package com.risediary.app.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecordValidationTest {
    @Test
    fun storageAcceptsBothSafeDerivedExtremesWithoutApplyingDirectInputBounds() {
        assertNull(RecordValidation.validateStoredQuantity(1_000, 100_000f))
        assertNull(RecordValidation.validateStoredQuantity(10_000, 1_000f))
    }

    @Test
    fun storageRejectsNonFiniteMissingOrUnsafeAmounts() {
        assertNotNull(RecordValidation.validateStoredQuantity(null, null))
        assertNotNull(RecordValidation.validateStoredQuantity(10_001, 1_000f))
        listOf(0f, -1f, 100_001f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertNotNull(RecordValidation.validateStoredQuantity(1, it))
        }
    }

    @Test
    fun newNotesAreLimitedButHistoricalLongNotesCanBePreservedOrShortened() {
        assertNull(RecordValidation.validateNote("x".repeat(10_000)))
        assertNotNull(RecordValidation.validateNote("x".repeat(10_001)))
        val historical = " x ".repeat(5_000)
        assertNull(RecordValidation.validateNote(historical, historical))
        assertNull(RecordValidation.validateNote(historical.dropLast(1), historical))
        assertNotNull(RecordValidation.validateNote(historical + "x", historical))
    }
    @Test
    fun acceptsSafeDerivedVolumeAboveDirectInputLimit() {
        assertNull(
            RecordValidation.validate(
                durationSeconds = 60,
                spurtCount = 501,
                volumeMl = 1_002f,
                distanceCm = null,
                distanceWasEntered = false
            )
        )
    }
    @Test
    fun acceptsBoundaryDurationAndValidVolume() {
        assertNull(
            RecordValidation.validate(
                durationSeconds = RecordValidation.MAX_DURATION_SECONDS,
                spurtCount = null,
                volumeMl = 2f,
                distanceCm = 0f,
                distanceWasEntered = true
            )
        )
    }

    @Test fun newDurationAcceptsTwentyFourHoursAndRejectsOneSecondMore() {
        assertNull(RecordValidation.validate(1, null, 2f, null, false))
        assertNull(RecordValidation.validate(86_400, null, 2f, null, false))
        for (seconds in listOf(-1, 0, 86_401)) {
            assertNotNull(RecordValidation.validate(seconds, null, 2f, null, false))
        }
    }

    @Test
    fun rejectsDurationBeyondTwentyFourHours() {
        assertNotNull(
            RecordValidation.validate(
                durationSeconds = RecordValidation.MAX_DURATION_SECONDS + 1,
                spurtCount = 1,
                volumeMl = null,
                distanceCm = null,
                distanceWasEntered = false
            )
        )
    }

    @Test
    fun acceptsUnchangedLegacyDurationWhenExplicitlyAllowed() {
        assertNull(
            RecordValidation.validate(
                durationSeconds = 3 * 60 * 60,
                spurtCount = 1,
                volumeMl = null,
                distanceCm = null,
                distanceWasEntered = false,
                allowLegacyDuration = true
            )
        )
    }

    @Test
    fun rejectsMissingVolumeAndInvalidDecimalDistance() {
        assertNotNull(
            RecordValidation.validate(
                durationSeconds = 30,
                spurtCount = null,
                volumeMl = null,
                distanceCm = null,
                distanceWasEntered = true
            )
        )
    }
}
