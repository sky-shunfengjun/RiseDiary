package com.risediary.app.util

import java.time.LocalDate

object StreakCalculator {
    fun currentStreak(dates: Iterable<LocalDate>): Int {
        val sorted = dates.distinct().sortedDescending()
        if (sorted.isEmpty()) return 0
        var streak = 1
        for (index in 1 until sorted.size) {
            if (sorted[index] == sorted[index - 1].minusDays(1)) streak++
            else break
        }
        return streak
    }
}
