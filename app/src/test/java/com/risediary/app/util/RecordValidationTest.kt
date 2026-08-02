package com.risediary.app.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecordValidationTest {
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

    @Test
    fun rejectsDurationBeyondOneHundredTwentyMinutes() {
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
