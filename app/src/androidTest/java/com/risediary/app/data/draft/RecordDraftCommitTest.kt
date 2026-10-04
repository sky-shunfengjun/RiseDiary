package com.risediary.app.data.draft

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.form.QuantityDraftSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDraftCommitTest {
    private lateinit var database: AppDatabase
    private val gate = DataMaintenanceGate()
    private fun repository() = RoomRecordDraftRepository(database, gate)
    private fun snapshot() = RecordDraftSnapshot("s1", sessionId = "s1",
        startTime = 100_000L, endTime = 112_000L, durationSeconds = 8, timingSource = "timer",
        quantity = QuantityDraftSnapshot("estimated", 23, "9.5", 80, true))
    private fun flight() = Flight(startTime = 100_000L, endTime = 112_000L, durationSeconds = 8,
        spurtCount = null, semenVolumeMl = 2.3f, volumeInputMode = "estimated",
        ejaculationDistanceCm = null, methodTags = "[]", moodNote = "", predictionMaxTicks = 80,
        recordDraftId = "s1", timingSource = "timer")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }
    @After fun tearDown() { database.close() }

    @Test fun restoredDraftRepeatedCommitInsertsOnlyOnce() = runBlocking {
        val saved = repository().save(snapshot(), null).getOrThrow()
        val first = repository().commit("s1", saved.revision, flight()).getOrThrow()
        val second = repository().commit("s1", saved.revision, flight()).getOrThrow()
        assertEquals(first, second)
        assertEquals(1, database.flightDao().getAll().size)
        assertNull(repository().loadPending().getOrThrow())
    }

    @Test fun staleRevisionCannotOverwriteOrCommitNewerInputs() = runBlocking {
        val first = repository().save(snapshot(), null).getOrThrow()
        val second = repository().save(first.copy(moodNote = "新的输入"), first.revision).getOrThrow()
        assertTrue(repository().save(first.copy(moodNote = "旧输入"), first.revision).isFailure)
        assertTrue(repository().commit("s1", first.revision, flight()).isFailure)
        assertEquals(second, repository().loadPending().getOrThrow())
        assertTrue(database.flightDao().getAll().isEmpty())
    }

    @Test fun pendingDraftBlocksAnotherCreationAndTimerDraftIsReused() = runBlocking {
        val first = repository().save(snapshot(), null).getOrThrow()
        assertTrue(repository().save(snapshot().copy(draftId = "other"), null).isFailure)
        val finished = TimerSession(status = TimerStatus.FINISHED, sessionId = "s1",
            startedAtEpochMillis = 100_000L, endedAtEpochMillis = 112_000L, elapsedMillis = 8_000L)
        assertEquals(first, repository().createFromTimer(finished).getOrThrow())
    }

    @Test fun failedCompletionRollsBackInsertedRecordAndCanRetry() = runBlocking {
        val saved = repository().save(snapshot(), null).getOrThrow()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_draft_completion BEFORE UPDATE ON record_drafts BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertTrue(repository().commit("s1", saved.revision, flight()).isFailure)
        assertTrue(database.flightDao().getAll().isEmpty())
        assertEquals(saved, repository().loadPending().getOrThrow())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_draft_completion")
        repository().commit("s1", saved.revision, flight()).getOrThrow()
        assertEquals(1, database.flightDao().getAll().size)
        assertNull(repository().loadPending().getOrThrow())
    }

    @Test fun completedDraftNeverBecomesPendingOnDelayedAutosave() = runBlocking {
        val saved = repository().save(snapshot(), null).getOrThrow()
        repository().commit("s1", saved.revision, flight()).getOrThrow()
        assertTrue(repository().save(saved.copy(moodNote = "晚到的输入"), saved.revision).isFailure)
        assertNull(repository().loadPending().getOrThrow())
        assertEquals(1, database.flightDao().getAll().size)
    }
}
