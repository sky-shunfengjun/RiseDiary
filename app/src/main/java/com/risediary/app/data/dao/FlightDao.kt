package com.risediary.app.data.dao

import androidx.room.*
import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flight: Flight): Long

    @Update
    suspend fun update(flight: Flight)

    @Delete
    suspend fun delete(flight: Flight)

    @Query("SELECT * FROM flights WHERE id = :id")
    suspend fun getById(id: Long): Flight?

    @Query("SELECT * FROM flights ORDER BY startTime DESC")
    fun getAllFlow(): Flow<List<Flight>>

    @Query("SELECT * FROM flights ORDER BY startTime DESC")
    suspend fun getAll(): List<Flight>

    // --- Statistics queries ---

    @Query("SELECT COUNT(*) FROM flights WHERE startTime >= :dayStart AND startTime < :dayEnd")
    suspend fun countByDay(dayStart: Long, dayEnd: Long): Int

    @Query("SELECT COUNT(*) FROM flights WHERE startTime >= :rangeStart AND startTime < :rangeEnd")
    suspend fun countByRange(rangeStart: Long, rangeEnd: Long): Int

    @Query("SELECT COALESCE(SUM(spurtCount), 0) FROM flights WHERE startTime >= :rangeStart AND startTime < :rangeEnd")
    suspend fun sumSpurtByRange(rangeStart: Long, rangeEnd: Long): Int

    @Query("SELECT COALESCE(SUM(semenVolumeMl), 0) FROM flights WHERE startTime >= :rangeStart AND startTime < :rangeEnd")
    suspend fun sumVolumeByRange(rangeStart: Long, rangeEnd: Long): Float

    @Query("SELECT COALESCE(AVG(durationSeconds), 0) FROM flights WHERE startTime >= :rangeStart AND startTime < :rangeEnd")
    suspend fun avgDurationByRange(rangeStart: Long, rangeEnd: Long): Float

    @Query("SELECT COALESCE(MAX(ejaculationDistanceCm), 0) FROM flights")
    suspend fun maxDistance(): Float

    @Query("SELECT * FROM flights WHERE ejaculationDistanceCm = (SELECT MAX(ejaculationDistanceCm) FROM flights) LIMIT 1")
    suspend fun maxDistanceFlight(): Flight?

    @Query("SELECT * FROM flights WHERE durationSeconds = (SELECT MAX(durationSeconds) FROM flights) LIMIT 1")
    suspend fun maxDurationFlight(): Flight?

    @Query("SELECT * FROM flights WHERE spurtCount = (SELECT MAX(spurtCount) FROM flights) LIMIT 1")
    suspend fun maxSpurtFlight(): Flight?

    @Query("SELECT * FROM flights WHERE semenVolumeMl = (SELECT MAX(semenVolumeMl) FROM flights) LIMIT 1")
    suspend fun maxVolumeFlight(): Flight?

    @Query("SELECT COUNT(*) FROM flights")
    suspend fun totalCount(): Int

    @Query("SELECT * FROM flights WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime DESC")
    suspend fun getByDay(dayStart: Long, dayEnd: Long): List<Flight>

    @Query("SELECT startTime FROM flights ORDER BY startTime DESC")
    suspend fun getAllStartTimes(): List<Long>

    @Query("SELECT * FROM flights ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<Flight>

    @Query("SELECT * FROM flights WHERE methodTags LIKE '%' || :tag || '%' ORDER BY startTime DESC")
    suspend fun getByTag(tag: String): List<Flight>

    @Query("SELECT COUNT(*) FROM flights WHERE methodTags LIKE '%' || :tag || '%'")
    suspend fun countByTag(tag: String): Int

    @Query("SELECT COUNT(*) FROM flights")
    suspend fun countAll(): Int

    @Query("SELECT startTime FROM flights WHERE startTime >= :since ORDER BY startTime ASC")
    suspend fun getStartTimesSince(since: Long): List<Long>

    @Query("SELECT * FROM flights WHERE startTime >= :since AND ejaculationDistanceCm IS NOT NULL ORDER BY startTime ASC")
    suspend fun getFlightsWithDistanceSince(since: Long): List<Flight>

    @Query("SELECT * FROM flights WHERE startTime >= :since ORDER BY startTime ASC")
    suspend fun getFlightsSince(since: Long): List<Flight>

    @Query("SELECT COALESCE(SUM(semenVolumeMl), 0) FROM flights")
    suspend fun sumTotalVolume(): Float

    @Query("DELETE FROM flights")
    suspend fun nuke()
}
