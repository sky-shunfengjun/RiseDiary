package com.risediary.app.util

import java.time.LocalDate

object StreakCalculator {
    /**
     * Counts the consecutive-day streak ending at [today] (or yesterday, i.e.
     * an ongoing streak). Returns 0 when the latest recorded date is older than
     * yesterday so backfilled old records cannot revive an expired streak.
     */
    fun currentStreak(dates: Iterable<LocalDate>, today: LocalDate): Int {
        val sorted = dates.distinct().sortedDescending()
        if (sorted.isEmpty()) return 0
        val anchor = sorted.first()
        if (anchor != today && anchor != today.minusDays(1)) return 0
        var streak = 1
        for (index in 1 until sorted.size) {
            if (sorted[index] == sorted[index - 1].minusDays(1)) streak++
            else break
        }
        return streak
    }
}
