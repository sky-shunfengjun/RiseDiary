package com.risediary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun countsDescendingConsecutiveDates() {
        val dates = listOf(
            LocalDate.of(2026, 7, 18),
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 7, 16),
            LocalDate.of(2026, 7, 14)
        )

        assertEquals(3, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun ongoingStreakCountsWhenLatestDateIsYesterday() {
        val dates = listOf(
            LocalDate.of(2026, 7, 18),
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 7, 16)
        )

        assertEquals(3, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun ignoresDuplicateDates() {
        val date = LocalDate.of(2026, 7, 18)
        assertEquals(
            2,
            StreakCalculator.currentStreak(listOf(date, date, date.minusDays(1)), today)
        )
    }

    @Test
    fun emptyInputHasNoStreak() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), today))
    }

    @Test
    fun staleDatesDoNotCountAsCurrentStreak() {
        val dates = listOf(
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 9),
            LocalDate.of(2026, 7, 8)
        )

        assertEquals(0, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun todayRecordCountsAsStartOfStreak() {
        val dates = listOf(today)
        assertEquals(1, StreakCalculator.currentStreak(dates, today))
    }
}
