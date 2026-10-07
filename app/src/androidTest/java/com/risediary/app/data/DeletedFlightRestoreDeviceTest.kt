package com.risediary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.RoomFlightRepository
import com.risediary.app.util.LocalCalendarContext
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual SQLite collision and identity tests; compiled here, executed only on a phone. */
@RunWith(AndroidJUnit4::class)
class DeletedFlightRestoreDeviceTest {
    private val original = Flight(id = 12, startTime = 1000, endTime = 61000, durationSeconds = 60,
        spurtCount = null, semenVolumeMl = 2.3f, ejaculationDistanceCm = null, methodTags = "[]", moodNote = "keep",
        legacySpurtCount = 3, legacyVolumeMl = 1.5f)
    private suspend fun withRepository(work: suspend (AppDatabase, RoomFlightRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try { work(db, RoomFlightRepository(db.flightDao(), LocalCalendarContext(Clock.systemUTC()), DataMaintenanceGate())) }
        finally { db.close() }
    }
    @Test fun legacyRecordWithNoSubmissionRestoresOriginalIdentityAndHistory() = runBlocking {
        withRepository { db, repo ->
            repo.restoreDeleted(original)
            assertEquals(original, db.flightDao().getById(original.id))
        }
    }
    @Test fun submittedRecordRestoresItsSubmissionAssociation() = runBlocking {
        withRepository { db, repo ->
            val submitted = original.copy(recordDraftId = "saved-submission")
            repo.restoreDeleted(submitted)
            assertEquals(submitted, db.flightDao().getById(submitted.id))
            assertEquals(submitted, db.flightDao().getByRecordDraftId("saved-submission"))
        }
    }
    @Test fun identityCollisionCannotReplaceLaterRecord() = runBlocking {
        withRepository { db, repo ->
            val later = original.copy(id = 13, moodNote = "later")
            db.flightDao().insertNew(later)
            assertTrue(runCatching { repo.restoreDeleted(original) }.isFailure)
            assertEquals(later, db.flightDao().getById(later.id))
            assertNull(db.flightDao().getById(original.id))
        }
    }
}
