package com.risediary.app.data.dao

import androidx.room.*
import com.risediary.app.data.entity.LengthRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface LengthRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: LengthRecord): Long

    @Update
    suspend fun update(record: LengthRecord)

    @Delete
    suspend fun delete(record: LengthRecord)

    @Query("SELECT * FROM length_records ORDER BY recordDate DESC")
    fun getAllFlow(): Flow<List<LengthRecord>>

    @Query("SELECT * FROM length_records ORDER BY recordDate DESC")
    suspend fun getAll(): List<LengthRecord>

    @Query("SELECT * FROM length_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LengthRecord?

    @Query("SELECT * FROM length_records WHERE recordDate >= :monthStart AND recordDate < :nextMonthStart LIMIT 1")
    suspend fun getForMonth(monthStart: Long, nextMonthStart: Long): LengthRecord?

    @Query("SELECT * FROM length_records WHERE recordDate >= :yearStart AND recordDate < :yearEnd ORDER BY recordDate ASC")
    suspend fun getForYear(yearStart: Long, yearEnd: Long): List<LengthRecord>

    @Query("SELECT COUNT(*) FROM length_records")
    suspend fun count(): Int

    @Query("SELECT * FROM length_records WHERE recordDate >= :since ORDER BY recordDate ASC")
    suspend fun getSince(since: Long): List<LengthRecord>

    @Query("SELECT COALESCE(MAX(erectLengthCm), 0) FROM length_records")
    suspend fun maxErectLength(): Float

    @Query("SELECT COALESCE(erectLengthCm, 0) FROM length_records WHERE erectLengthCm > 0 ORDER BY recordDate ASC, id ASC LIMIT 1")
    suspend fun firstErectLength(): Float

    @Query("DELETE FROM length_records")
    suspend fun nuke()
}
