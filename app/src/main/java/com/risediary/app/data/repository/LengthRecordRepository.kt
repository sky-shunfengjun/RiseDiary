package com.risediary.app.data.repository

import com.risediary.app.data.dao.LengthRecordDao
import com.risediary.app.data.entity.LengthRecord
import kotlinx.coroutines.flow.Flow
import com.risediary.app.util.LocalCalendarContext
import com.risediary.app.util.LocalTimeRanges
import javax.inject.Inject
import com.risediary.app.data.DataMaintenanceGate

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
    private val calendar: LocalCalendarContext,
    private val maintenanceGate: DataMaintenanceGate = DataMaintenanceGate()
) : LengthRecordRepository {
    override val allRecords: Flow<List<LengthRecord>> = dao.getAllFlow()

    override suspend fun insert(record: LengthRecord): Long = maintenanceGate.write { dao.insert(record) }
    override suspend fun update(record: LengthRecord) = maintenanceGate.write { dao.update(record) }
    override suspend fun delete(record: LengthRecord) = maintenanceGate.write {
        maintenanceGate.requireCurrent(record, dao.getById(record.id))
        dao.delete(record)
    }
    override suspend fun getById(id: Long): LengthRecord? = dao.getById(id)
    override suspend fun getAll(): List<LengthRecord> = dao.getAll()

    override suspend fun getCurrentMonthRecord(): LengthRecord? {
        val snapshot = calendar.current()
        val (start, end) = LocalTimeRanges.monthContaining(snapshot.date, snapshot.zoneId)
        return dao.getForMonth(start, end)
    }

    override suspend fun getThisYearRecords(): List<LengthRecord> {
        val snapshot = calendar.current()
        val (start, end) = LocalTimeRanges.yearContaining(snapshot.date, snapshot.zoneId)
        return dao.getForYear(start, end)
    }

    override suspend fun count(): Int = dao.count()
    override suspend fun getSince(since: Long): List<LengthRecord> = dao.getSince(since)
    override suspend fun maxErectLength(): Float = dao.maxErectLength()
    override suspend fun firstErectLength(): Float? = dao.firstErectLength()

}
