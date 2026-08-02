package com.risediary.app.data.repository

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import java.time.LocalDate
import com.risediary.app.util.StreakCalculator
import javax.inject.Inject
import javax.inject.Singleton

data class AchievementUnlock(val key: String)

@Singleton
class AchievementDetector @Inject constructor(
    private val flightRepository: FlightRepository,
    private val lengthRecordRepository: LengthRecordRepository,
    private val achievementRepository: AchievementRepository
) {
    suspend fun checkAndUnlock(flight: Flight): List<AchievementUnlock> {
        val candidates = linkedSetOf<String>()
        val totalCount = flightRepository.totalCount()

        listOf(
            1 to "milestone_1",
            10 to "milestone_10",
            50 to "milestone_50",
            100 to "milestone_100",
            500 to "milestone_500"
        ).forEach { (threshold, key) ->
            if (totalCount >= threshold) candidates += key
        }

        val distance = flight.ejaculationDistanceCm ?: 0f
        if (distance >= 30f) candidates += "record_distance_30"
        if (distance >= 80f) candidates += "record_distance_80"
        if (flight.durationSeconds >= 30 * 60) candidates += "record_duration_30"
        if (flight.durationSeconds >= 60 * 60) candidates += "record_duration_60"
        if (flight.durationSeconds >= 90 * 60) candidates += "marathon"
        if (flight.durationSeconds in 1..(3 * 60)) candidates += "speedster"

        val volume = flight.semenVolumeMl ?: 0f
        if ((flight.spurtCount ?: 0) >= 15 || volume >= 30f) {
            candidates += "record_volume_high"
        }

        val distinctTags = flightRepository.getRecent(1000)
            .flatMap { TagJson.decode(it.methodTags) }
            .distinct()
        if (distinctTags.size >= 5) candidates += "tag_5_types"

        val totalVolume = flightRepository.sumTotalVolume()
        if (totalVolume >= 100f) candidates += "volume_100ml"
        if (totalVolume >= 500f) candidates += "volume_500ml"

        val streak = calculateCurrentStreak()
        if (streak >= 7) candidates += "streak_7"
        if (streak >= 30) {
            candidates += "streak_30"
            candidates += "special_30day_record"
        }
        if (streak >= 90) candidates += "streak_90"

        return unlockAll(candidates)
    }

    suspend fun checkLengthAchievements(
        @Suppress("UNUSED_PARAMETER") record: LengthRecord
    ): List<AchievementUnlock> {
        val candidates = linkedSetOf<String>()
        if (lengthRecordRepository.count() >= 1) candidates += "length_first"

        val firstErect = lengthRecordRepository.firstErectLength()
        val maxErect = lengthRecordRepository.maxErectLength()
        if (firstErect > 0f && maxErect - firstErect >= 2f) {
            candidates += "length_growth_2cm"
        }
        return unlockAll(candidates)
    }

    suspend fun calculateCurrentStreak(): Int {
        val dates = flightRepository.getDistinctFlightDates()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        return StreakCalculator.currentStreak(dates)
    }

    private suspend fun unlockAll(keys: Iterable<String>): List<AchievementUnlock> =
        keys.mapNotNull { key ->
            achievementRepository.unlock(key)?.let { AchievementUnlock(key) }
        }
}
