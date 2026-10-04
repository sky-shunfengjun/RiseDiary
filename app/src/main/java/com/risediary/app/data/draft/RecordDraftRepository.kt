package com.risediary.app.data.draft

import androidx.room.withTransaction
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.form.QuantityDraftSnapshot
import com.risediary.app.util.RecordTimingPolicy
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface RecordDraftRepository {
    suspend fun loadPending(): Result<RecordDraftSnapshot?>
    suspend fun save(snapshot: RecordDraftSnapshot, expectedRevision: Long?): Result<RecordDraftSnapshot>
    suspend fun createFromTimer(finished: TimerSession, predictionMaxTicks: Int = 80): Result<RecordDraftSnapshot>
    suspend fun commit(draftId: String, expectedRevision: Long, flight: Flight): Result<Long>
    suspend fun discard(draftId: String): Result<Unit>
}

@Singleton
class RoomRecordDraftRepository @Inject constructor(
    private val database: AppDatabase,
    private val gate: DataMaintenanceGate
) : RecordDraftRepository {
    private val dao get() = database.recordDraftDao()
    private fun decode(row: RecordDraftEntity) = RecordDraftCodec.decode(row.payload).also {
        check(it.draftId == row.draftId && it.revision == row.revision) { "草稿信息损坏，请重试" }
    }

    override suspend fun loadPending(): Result<RecordDraftSnapshot?> = attempt {
        gate.write { dao.getPending()?.let(::decode) }
    }

    override suspend fun save(snapshot: RecordDraftSnapshot, expectedRevision: Long?): Result<RecordDraftSnapshot> = attempt {
        gate.write { database.withTransaction { saveLocked(snapshot, expectedRevision) } }
    }

    private suspend fun saveLocked(snapshot: RecordDraftSnapshot, expectedRevision: Long?): RecordDraftSnapshot {
        val current = dao.getById(snapshot.draftId)
        if (current == null) {
            check(expectedRevision == null) { "草稿已移除，请重新打开" }
            check(dao.getPending() == null) { "请先处理未保存的记录" }
            check(database.flightDao().getByRecordDraftId(snapshot.draftId) == null) { "记录已保存" }
            val saved = snapshot.copy(revision = 1L)
            dao.insert(RecordDraftEntity(saved.draftId, 1, 1L, RecordDraftCodec.encode(saved), null))
            return saved
        }
        if (current.activeSlot != 1 || expectedRevision != current.revision) throw RecordDraftConflictException()
        decode(current)
        val saved = snapshot.copy(revision = current.revision + 1L)
        if (dao.updatePending(snapshot.draftId, current.revision, RecordDraftCodec.encode(saved)) != 1)
            throw RecordDraftConflictException()
        return saved
    }

    override suspend fun createFromTimer(finished: TimerSession, predictionMaxTicks: Int): Result<RecordDraftSnapshot> = attempt {
        require(finished.isTerminal && finished.finishCandidate == null)
        val id = requireNotNull(finished.sessionId)
        gate.write {
            database.withTransaction {
                database.flightDao().getByRecordDraftId(id)?.let { throw DraftAlreadyCommittedException(it.id) }
                dao.getById(id)?.let {
                    check(it.activeSlot == 1) { "这次记录已处理" }
                    return@withTransaction decode(it)
                }
                val duration = (finished.elapsedMillis / 1_000L).toInt().coerceIn(1, 86400)
                val start = finished.startedAtEpochMillis
                saveLocked(RecordDraftSnapshot(id, sessionId = id, startTime = start,
                    endTime = finished.endedAtEpochMillis ?: (start + duration * 1_000L),
                    durationSeconds = duration,
                    timingSource = if (finished.endedAtEpochMillis == null) "manual" else "timer",
                    quantity = QuantityDraftSnapshot("estimated", 0, "", predictionMaxTicks, false),
                    video = finished.video?.video), null)
            }
        }
    }

    override suspend fun commit(draftId: String, expectedRevision: Long, flight: Flight): Result<Long> = attempt {
        gate.write {
            database.withTransaction {
                database.flightDao().getByRecordDraftId(draftId)?.let { return@withTransaction it.id }
                val current = dao.getById(draftId) ?: throw RecordDraftConflictException()
                if (current.activeSlot != 1 || current.revision != expectedRevision) throw RecordDraftConflictException()
                val snapshot = decode(current)
                check(snapshot.startTime == flight.startTime && snapshot.endTime == flight.endTime &&
                    snapshot.durationSeconds == flight.durationSeconds && snapshot.timingSource == flight.timingSource)
                require(RecordTimingPolicy.validate(flight.startTime, flight.endTime, flight.durationSeconds, flight.timingSource) == null)
                val id = database.flightDao().insert(flight.copy(id = 0L, recordDraftId = draftId))
                check(dao.markCompleted(draftId, id) == 1)
                id
            }
        }
    }

    override suspend fun discard(draftId: String): Result<Unit> = attempt {
        gate.write {
            database.withTransaction {
                val current = dao.getById(draftId)
                if (current?.activeSlot == 1) dao.deleteById(draftId)
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (failure: DataMaintenanceBusyException) {
        Result.failure(failure)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
