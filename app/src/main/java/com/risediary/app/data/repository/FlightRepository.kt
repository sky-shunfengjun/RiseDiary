package com.risediary.app.data.repository

import com.risediary.app.data.dao.FlightDao
import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.flow.Flow
import com.risediary.app.util.LocalTimeRanges
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface FlightRepository {
    val allFlights: Flow<List<Flight>>

    suspend fun insert(flight: Flight): Long
    suspend fun update(flight: Flight)
    suspend fun delete(flight: Flight)
    suspend fun getById(id: Long): Flight?
    suspend fun getAll(): List<Flight>
    suspend fun getAllStartTimes(): List<Long>
    suspend fun countToday(): Int
    suspend fun countByRange(start: Long, end: Long): Int
    suspend fun countYesterday(): Int
    suspend fun getTodayFlights(): List<Flight>
    fun todayRange(): Pair<Long, Long>
    suspend fun countThisWeek(): Int
    suspend fun countLastWeek(): Int
    suspend fun countThisMonth(): Int
    suspend fun countThisYear(): Int
    suspend fun sumSpurtThisWeek(): Int
    suspend fun sumVolumeThisWeek(): Float
    suspend fun avgDurationThisWeek(): Float
    suspend fun avgDurationAll(): Float
    suspend fun maxDistance(): Float
    suspend fun totalCount(): Int
    suspend fun totalDistinctDays(): Int
    suspend fun getDistinctFlightDates(): List<String>
    suspend fun getRecent(limit: Int = 100): List<Flight>
    suspend fun getByTag(tag: String): List<Flight>
    suspend fun countByTag(tag: String): Int
    suspend fun getDayCountsSince(since: Long): Map<String, Int>
    suspend fun getFlightsWithDistanceSince(since: Long): List<Flight>
    suspend fun getFlightsSince(since: Long): List<Flight>
    suspend fun sumTotalVolume(): Float
}

class RoomFlightRepository @Inject constructor(
    private val dao: FlightDao,
    private val clock: Clock,
    private val zoneId: ZoneId
) : FlightRepository {

    override val allFlights: Flow<List<Flight>> = dao.getAllFlow()

    override suspend fun insert(flight: Flight): Long = dao.insert(flight)
    override suspend fun update(flight: Flight) = dao.update(flight)
    override suspend fun delete(flight: Flight) = dao.delete(flight)
    override suspend fun getById(id: Long): Flight? = dao.getById(id)
    override suspend fun getAll(): List<Flight> = dao.getAll()
    override suspend fun getAllStartTimes(): List<Long> = dao.getAllStartTimes()

    override suspend fun countToday(): Int {
        val (start, end) = todayRange()
        return dao.countByDay(start, end)
    }

    override suspend fun countByRange(start: Long, end: Long): Int =
        dao.countByRange(start, end)

    override suspend fun countYesterday(): Int {
        val yesterday = LocalDate.now(clock).minusDays(1)
        val (start, end) = LocalTimeRanges.day(yesterday, zoneId)
        return dao.countByDay(start, end)
    }

    override suspend fun getTodayFlights(): List<Flight> {
        val (start, end) = todayRange()
        return dao.getByDay(start, end)
    }

    override fun todayRange(): Pair<Long, Long> =
        LocalTimeRanges.day(LocalDate.now(clock), zoneId)

    override suspend fun countThisWeek(): Int {
        val (start, end) = LocalTimeRanges.weekContaining(LocalDate.now(clock), zoneId)
        return dao.countByRange(start, end)
    }

    override suspend fun countLastWeek(): Int {
        val (start, end) =
            LocalTimeRanges.weekContaining(LocalDate.now(clock).minusWeeks(1), zoneId)
        return dao.countByRange(start, end)
    }

    override suspend fun countThisMonth(): Int {
        val (start, end) = LocalTimeRanges.monthContaining(LocalDate.now(clock), zoneId)
        return dao.countByRange(start, end)
    }

    override suspend fun countThisYear(): Int {
        val (start, end) = LocalTimeRanges.yearContaining(LocalDate.now(clock), zoneId)
        return dao.countByRange(start, end)
    }

    override suspend fun sumSpurtThisWeek(): Int {
        val (start, end) = LocalTimeRanges.weekContaining(LocalDate.now(clock), zoneId)
        return dao.sumSpurtByRange(start, end)
    }

    override suspend fun sumVolumeThisWeek(): Float {
        val (start, end) = LocalTimeRanges.weekContaining(LocalDate.now(clock), zoneId)
        return dao.sumVolumeByRange(start, end)
    }

    override suspend fun avgDurationThisWeek(): Float {
        val (start, end) = LocalTimeRanges.weekContaining(LocalDate.now(clock), zoneId)
        return dao.avgDurationByRange(start, end)
    }

    override suspend fun avgDurationAll(): Float =
        dao.avgDurationByRange(0L, Long.MAX_VALUE)

    override suspend fun maxDistance(): Float = dao.maxDistance()
    override suspend fun totalCount(): Int = dao.totalCount()

    override suspend fun totalDistinctDays(): Int =
        dao.getAllStartTimes().asSequence()
            .map { LocalTimeRanges.localDate(it, zoneId) }
            .distinct()
            .count()

    override suspend fun getDistinctFlightDates(): List<String> =
        dao.getAllStartTimes()
            .asSequence()
            .map { LocalTimeRanges.localDate(it, zoneId) }
            .distinct()
            .sortedDescending()
            .map(LocalDate::toString)
            .toList()

    override suspend fun getRecent(limit: Int): List<Flight> = dao.getRecent(limit)

    override suspend fun getByTag(tag: String): List<Flight> =
        dao.getAll().filter { flight -> TagJson.decode(flight.methodTags).contains(tag) }

    override suspend fun countByTag(tag: String): Int = getByTag(tag).size

    override suspend fun getDayCountsSince(since: Long): Map<String, Int> =
        dao.getStartTimesSince(since)
            .groupingBy { LocalTimeRanges.localDate(it, zoneId).toString() }
            .eachCount()

    override suspend fun getFlightsWithDistanceSince(since: Long): List<Flight> =
        dao.getFlightsWithDistanceSince(since)

    override suspend fun getFlightsSince(since: Long): List<Flight> =
        dao.getFlightsSince(since)

    override suspend fun sumTotalVolume(): Float = dao.sumTotalVolume()

}
