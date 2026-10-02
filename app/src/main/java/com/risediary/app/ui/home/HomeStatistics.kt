package com.risediary.app.ui.home

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.util.LocalCalendarSnapshot
import com.risediary.app.util.LocalTimeRanges
import java.time.temporal.ChronoUnit

/** One complete result is published, using one calendar for every period and grouping. */
internal data class HomeStatistics(
    val calendar: LocalCalendarSnapshot,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val monthCount: Int = 0,
    val yearCount: Int = 0,
    val weekSpurtSum: Int = 0,
    val weekVolumeSum: Float = 0f,
    val avgDuration: Float = 0f,
    val maxDistance: Float = 0f,
    val totalCount: Int = 0,
    val lastWeekCount: Int = 0,
    val averageIntervalDays: Float? = null,
    val currentMonthLength: LengthRecord? = null,
    val lengthRecords: List<LengthRecord> = emptyList(),
    val lastFlightDaysAgo: Int? = null,
    val recentFlights: List<Flight> = emptyList(),
    val flightCountsByDay: Map<String, Int> = emptyMap(),
    val trendFlights: List<Flight> = emptyList()
) {
    companion object {
        fun calculate(
            flights: List<Flight>, lengths: List<LengthRecord>,
            calendar: LocalCalendarSnapshot, nowMillis: Long
        ): HomeStatistics {
            val (date, zone) = calendar
            fun Flight.inRange(range: Pair<Long, Long>): Boolean =
                startTime >= range.first && startTime < range.second
            val todayRange = LocalTimeRanges.day(date, zone)
            val weekRange = LocalTimeRanges.weekContaining(date, zone)
            val lastWeekRange = LocalTimeRanges.weekContaining(date.minusWeeks(1), zone)
            val monthRange = LocalTimeRanges.monthContaining(date, zone)
            val yearRange = LocalTimeRanges.yearContaining(date, zone)
            val week = flights.filter { it.inRange(weekRange) }
            val recent = flights.sortedByDescending(Flight::startTime).take(30)
            val sortedLengths = lengths.sortedByDescending(LengthRecord::recordDate)
            val yearAgo = nowMillis - 365L * 86_400_000L
            val lastFlightDate = recent.firstOrNull()?.let {
                LocalTimeRanges.localDate(it.startTime, zone)
            }
            return HomeStatistics(
                calendar = calendar,
                todayCount = flights.count { it.inRange(todayRange) },
                weekCount = week.size,
                monthCount = flights.count { it.inRange(monthRange) },
                yearCount = flights.count { it.inRange(yearRange) },
                weekSpurtSum = week.sumOf { it.spurtCount ?: 0 },
                weekVolumeSum = week.fold(0f) { sum, flight -> sum + (flight.semenVolumeMl ?: 0f) },
                avgDuration = if (flights.isEmpty()) 0f else flights.map { it.durationSeconds }.average().toFloat(),
                maxDistance = flights.mapNotNull(Flight::ejaculationDistanceCm).maxOrNull() ?: 0f,
                totalCount = flights.size,
                lastWeekCount = flights.count { it.inRange(lastWeekRange) },
                averageIntervalDays = calculateAverageIntervalDays(flights.map(Flight::startTime)),
                currentMonthLength = sortedLengths.firstOrNull {
                    it.recordDate >= monthRange.first && it.recordDate < monthRange.second
                },
                lengthRecords = sortedLengths,
                lastFlightDaysAgo = lastFlightDate?.let {
                    ChronoUnit.DAYS.between(it, date).coerceAtLeast(0).toInt()
                },
                recentFlights = recent,
                flightCountsByDay = flights.asSequence().filter { it.startTime >= yearAgo }
                    .groupingBy { LocalTimeRanges.localDate(it.startTime, zone).toString() }.eachCount(),
                trendFlights = flights.filter { it.startTime >= yearAgo }.sortedBy(Flight::startTime)
            )
        }
    }
}
