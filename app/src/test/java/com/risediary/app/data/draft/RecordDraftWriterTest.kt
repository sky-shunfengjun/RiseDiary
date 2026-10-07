package com.risediary.app.data.draft

import com.risediary.app.data.entity.Flight
import com.risediary.app.service.TimerSession
import com.risediary.app.ui.form.QuantityDraftSnapshot
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDraftWriterTest {
    private fun initial() = RecordDraftSnapshot("draft", revision = 1L, startTime = 100_000L,
        endTime = 160_000L, durationSeconds = 60, quantity = QuantityDraftSnapshot("estimated", 0, "", 80, false))

    @Test fun rapidEditsSaveNewestFieldsWithTheLatestSuccessfulRevision() = runTest {
        val disk = DraftDisk(initial())
        val writer = RecordDraftWriter(disk, disk.current, backgroundScope)
        writer.update(initial().copy(moodNote = "first"))
        runCurrent()
        writer.update(initial().copy(moodNote = "second", quantity = QuantityDraftSnapshot("milliliters", 23, "9.5", 80, true)))
        writer.update(initial().copy(moodNote = "last", quantity = QuantityDraftSnapshot("milliliters", 23, "9.5", 80, true)))
        val result = writer.flush().getOrThrow()
        assertEquals("last", disk.current.moodNote)
        assertEquals("9.5", disk.current.quantity.manualText)
        assertEquals(23, disk.current.quantity.estimatedTicks)
        assertEquals(disk.current.revision, result.revision)
        assertTrue(result.revision >= 3L)
        writer.close()
    }

    @Test fun storageFailureRetainsInputForExplicitFlushRetry() = runTest {
        val disk = DraftDisk(initial()).apply { fail = true }
        val writer = RecordDraftWriter(disk, disk.current, backgroundScope)
        writer.update(initial().copy(moodNote = "kept"))
        assertTrue(writer.flush().isFailure)
        assertEquals("", disk.current.moodNote)
        disk.fail = false
        assertEquals("kept", writer.flush().getOrThrow().moodNote)
        assertEquals("kept", disk.current.moodNote)
        writer.close()
    }

    @Test fun stalePageCannotOverwriteARevisionWrittenByAnotherPage() = runTest {
        val disk = DraftDisk(initial())
        val writer = RecordDraftWriter(disk, disk.current, backgroundScope)
        disk.current = initial().copy(revision = 2L, moodNote = "new page")
        writer.update(initial().copy(moodNote = "old page"))
        assertTrue(writer.flush().isFailure)
        assertEquals("new page", disk.current.moodNote)
        assertEquals(2L, disk.current.revision)
        writer.close()
    }

    private class DraftDisk(var current: RecordDraftSnapshot) : RecordDraftRepository {
        var fail = false
        override suspend fun loadPending() = Result.success(current)
        override suspend fun save(snapshot: RecordDraftSnapshot, expectedRevision: Long?): Result<RecordDraftSnapshot> {
            if (fail) return Result.failure(IOException("disk unavailable"))
            if (expectedRevision != current.revision) return Result.failure(RecordDraftConflictException())
            current = RecordDraftCodec.decode(RecordDraftCodec.encode(snapshot.copy(revision = current.revision + 1)))
            return Result.success(current)
        }
        override suspend fun createFromTimer(finished: TimerSession, predictionMaxTicks: Int) = error("unused")
        override suspend fun commit(draftId: String, expectedRevision: Long, flight: Flight) = error("unused")
        override suspend fun discard(draftId: String) = error("unused")
    }
}
