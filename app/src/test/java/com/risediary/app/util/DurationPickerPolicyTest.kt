package com.risediary.app.util

import org.junit.Assert.*
import org.junit.Test

class DurationPickerPolicyTest {
    @Test fun durationBreaksIntoThreeColumnsWithoutLosingLongValues() {
        assertEquals(DurationParts(0, 0, 1), durationPartsFromSeconds(1))
        assertEquals(DurationParts(0, 59, 59), durationPartsFromSeconds(3_599))
        assertEquals(DurationParts(1, 0, 0), durationPartsFromSeconds(3_600))
        assertEquals(DurationParts(23, 59, 59), durationPartsFromSeconds(86_399))
        assertEquals(DurationParts(24, 0, 0), durationPartsFromSeconds(86_400))
    }

    @Test fun confirmationUsesAllThreeColumns() {
        assertEquals(0, durationSecondsFromParts(DurationParts(0, 0, 0)))
        assertEquals(3_661, durationSecondsFromParts(DurationParts(1, 1, 1)))
        assertEquals(86_399, durationSecondsFromParts(DurationParts(23, 59, 59)))
        assertEquals(86_400, durationSecondsFromParts(DurationParts(24, 0, 0)))
    }

    @Test fun openingPickerClampsInvalidValuesToSupportedEndpoints() {
        assertEquals(DurationParts(0, 0, 0), durationPartsFromSeconds(-1))
        assertEquals(DurationParts(24, 0, 0), durationPartsFromSeconds(Int.MAX_VALUE))
    }

    @Test fun selectingHourTwentyFourResetsOtherColumnsAndGoingBackKeepsZero() {
        val atLimit = durationPartsWithHours(DurationParts(23, 59, 59), 24)
        assertEquals(DurationParts(24, 0, 0), atLimit)
        assertEquals(DurationParts(23, 0, 0), durationPartsWithHours(atLimit, 23))
        assertEquals(DurationParts(1, 12, 34), durationPartsWithHours(DurationParts(0, 12, 34), 1))
    }

    @Test fun invalidColumnsAndValuesBeyondTwentyFourHoursCannotConfirm() {
        listOf(DurationParts(-1, 0, 0), DurationParts(25, 0, 0),
            DurationParts(0, -1, 0), DurationParts(0, 60, 0),
            DurationParts(0, 0, -1), DurationParts(0, 0, 60),
            DurationParts(24, 1, 0), DurationParts(24, 0, 1)
        ).forEach { assertTrue(runCatching { durationSecondsFromParts(it) }.isFailure) }
    }
}