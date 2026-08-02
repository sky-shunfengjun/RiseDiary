package com.risediary.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AverageIntervalTest {
    @Test
    fun `average interval uses every supplied record`() {
        val day = 86_400_000L
        val oneHundredOneRecords = (0L..99L).map { it * day } + 199L * day

        assertEquals(1.99f, calculateAverageIntervalDays(oneHundredOneRecords)!!, 0.0001f)
    }

    @Test
    fun `average interval needs two distinct timestamps`() {
        assertNull(calculateAverageIntervalDays(listOf(123L, 123L)))
    }
}
