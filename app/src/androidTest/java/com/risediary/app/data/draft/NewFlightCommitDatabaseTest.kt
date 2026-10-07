package com.risediary.app.data.draft

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UnsubmittedFormCleanup
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.RoomFlightRepository
import com.risediary.app.util.LocalCalendarContext
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewFlightCommitDatabaseTest {
    private fun flight() = Flight(startTime = 100_000L, endTime = 120_000L, durationSeconds = 8,
        spurtCount = null, semenVolumeMl = 2.3f, ejaculationDistanceCm = null, methodTags = "[]",
        moodNote = "first", recordDraftId = "submission", timingSource = "timer")

    @Test fun concurrentSaveAttemptsReturnTheSameCommittedRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = RoomFlightRepository(database.flightDao(), LocalCalendarContext(java.time.Clock.systemUTC()), DataMaintenanceGate())
            val results = withContext(Dispatchers.IO) {
                listOf(async { repository.insertOnce(flight()) },
                    async { repository.insertOnce(flight().copy(moodNote = "second")) }).awaitAll()
            }
            assertEquals(results[0], results[1])
            assertEquals(1, database.flightDao().getAll().size)
            val retried = repository.insertOnce(flight().copy(moodNote = "changed retry"))
            assertEquals(results[0], retried)
        } finally { database.close() }
    }

    @Test fun reopeningAnOldDatabaseDeletesOnlyUnsubmittedForms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "dev14-cleanup-" + UUID.randomUUID() + ".db"
        var database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val saved = flight().copy(spurtCount = 3, legacySpurtCount = 3, legacyVolumeMl = 2.3f)
            val id = database.flightDao().insert(saved)
            database.recordDraftDao().insert(RecordDraftEntity("pending", 1, 1L, "old unsubmitted content", null))
            database.recordDraftDao().insert(RecordDraftEntity("completed", null, 1L, "completed metadata", id))
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addCallback(UnsubmittedFormCleanup).build()
            assertNull(database.recordDraftDao().getPending())
            assertNull(database.recordDraftDao().getById("pending"))
            assertNotNull(database.recordDraftDao().getById("completed"))
            assertEquals(saved.copy(id = id), database.flightDao().getById(id))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
