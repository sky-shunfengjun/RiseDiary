package com.risediary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    @Test
    fun countsDescendingConsecutiveDates() {
        val dates = listOf(
            LocalDate.of(2026, 7, 18),
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 7, 16),
            LocalDate.of(2026, 7, 14)
        )

        assertEquals(3, StreakCalculator.currentStreak(dates))
    }

    @Test
    fun ignoresDuplicateDates() {
        val date = LocalDate.of(2026, 7, 18)
        assertEquals(
            2,
            StreakCalculator.currentStreak(listOf(date, date, date.minusDays(1)))
        )
    }

    @Test
    fun emptyInputHasNoStreak() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList()))
    }
}
