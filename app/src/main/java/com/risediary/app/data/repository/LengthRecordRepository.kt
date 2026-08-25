package com.risediary.app.data.repository

import com.risediary.app.data.dao.LengthRecordDao
import com.risediary.app.data.entity.LengthRecord
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface LengthRecordRepository {
    val allRecords: Flow<List<LengthRecord>>

    suspend fun insert(record: LengthRecord): Long
    suspend fun update(record: LengthRecord)
    suspend fun delete(record: LengthRecord)
    suspend fun getById(id: Long): LengthRecord?
    suspend fun getAll(): List<LengthRecord>
    suspend fun getCurrentMonthRecord(): LengthRecord?
    suspend fun getThisYearRecords(): List<LengthRecord>
    suspend fun count(): Int
    suspend fun getSince(since: Long): List<LengthRecord>
    suspend fun maxErectLength(): Float
    suspend fun firstErectLength(): Float?
}

class RoomLengthRecordRepository @Inject constructor(
    private val dao: LengthRecordDao,
    private val clock: Clock,
    private val zoneId: ZoneId
) : LengthRecordRepository {
    override val allRecords: Flow<List<LengthRecord>> = dao.getAllFlow()

    override suspend fun insert(record: LengthRecord): Long = dao.insert(record)
    override suspend fun update(record: LengthRecord) = dao.update(record)
    override suspend fun delete(record: LengthRecord) = dao.delete(record)
    override suspend fun getById(id: Long): LengthRecord? = dao.getById(id)
    override suspend fun getAll(): List<LengthRecord> = dao.getAll()

    override suspend fun getCurrentMonthRecord(): LengthRecord? {
        val now = LocalDate.now(clock)
        val start = now.withDayOfMonth(1)
        return dao.getForMonth(start.toEpochMillis(), start.plusMonths(1).toEpochMillis())
    }

    override suspend fun getThisYearRecords(): List<LengthRecord> {
        val now = LocalDate.now(clock)
        val start = now.withDayOfYear(1)
        return dao.getForYear(start.toEpochMillis(), start.plusYears(1).toEpochMillis())
    }

    override suspend fun count(): Int = dao.count()
    override suspend fun getSince(since: Long): List<LengthRecord> = dao.getSince(since)
    override suspend fun maxErectLength(): Float = dao.maxErectLength()
    override suspend fun firstErectLength(): Float? = dao.firstErectLength()

    private fun LocalDate.toEpochMillis(): Long =
        atStartOfDay(zoneId).toInstant().toEpochMilli()
}
