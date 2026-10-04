package com.risediary.app.data.draft

import androidx.room.*

@Dao
interface RecordDraftDao {
    @Query("SELECT * FROM record_drafts WHERE draftId = :draftId")
    suspend fun getById(draftId: String): RecordDraftEntity?
    @Query("SELECT * FROM record_drafts WHERE activeSlot = 1 LIMIT 1")
    suspend fun getPending(): RecordDraftEntity?
    @Query("SELECT * FROM record_drafts")
    suspend fun getAll(): List<RecordDraftEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RecordDraftEntity)
    @Query("UPDATE record_drafts SET payload = :payload, revision = revision + 1 WHERE draftId = :draftId AND revision = :expectedRevision AND activeSlot = 1")
    suspend fun updatePending(draftId: String, expectedRevision: Long, payload: String): Int
    @Query("UPDATE record_drafts SET activeSlot = NULL, completedFlightId = :flightId WHERE draftId = :draftId")
    suspend fun markCompleted(draftId: String, flightId: Long): Int
    @Query("DELETE FROM record_drafts WHERE draftId = :draftId")
    suspend fun deleteById(draftId: String)
    @Query("DELETE FROM record_drafts")
    suspend fun nuke()
}
