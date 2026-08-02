package com.risediary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {
    @Test
    fun formDurationNeverConvertsMinutesToHours() {
        assertEquals("59分59秒", formatFormDuration(3_599))
        assertEquals("60分00秒", formatFormDuration(3_600))
        assertEquals("61分05秒", formatFormDuration(3_665))
        assertEquals("119分59秒", formatFormDuration(7_199))
        assertEquals("120分00秒", formatFormDuration(7_200))
    }

    @Test
    fun naturalDurationUsesHoursOutsideTheForm() {
        assertEquals("1小时1分5秒", formatNaturalDuration(3_665))
        assertEquals("2小时", formatNaturalDuration(7_200))
        assertEquals("2小时", formatNaturalDuration(7_200, includeSeconds = false))
    }

    @Test
    fun timerClockAlwaysKeepsHoursColumn() {
        assertEquals("00:00:00", formatTimerClock(0L))
        assertEquals("01:01:05", formatTimerClock(3_665_000L))
        assertEquals("02:00:00", formatTimerClock(7_200_000L))
    }
}
