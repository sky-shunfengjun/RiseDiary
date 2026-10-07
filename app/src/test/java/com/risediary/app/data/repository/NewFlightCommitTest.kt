package com.risediary.app.data.repository

import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NewFlightCommitTest {
    private fun flight() = Flight(startTime = 1_000L, endTime = 61_000L, durationSeconds = 60,
        spurtCount = null, semenVolumeMl = 2.3f, ejaculationDistanceCm = null,
        methodTags = "[]", moodNote = "first value", createdAt = 1_000L, updatedAt = 61_000L,
        recordDraftId = "submission-7")

    @Test fun retryAfterCommitReturnsTheSavedRecordWithoutReplacingItsValues() = runTest {
        val rows = linkedMapOf<String, Flight>()
        var nextId = 10L
        suspend fun commit(value: Flight) = commitNewFlightOnce(value,
            readExisting = { rows[it] },
            insert = { input -> val id = nextId++; rows[input.recordDraftId!!] = input.copy(id = id); id })
        val first = commit(flight())
        val retry = commit(flight().copy(moodNote = "later unsaved changes"))
        assertEquals(10L, retry.id)
        assertEquals("first value", retry.moodNote)
        assertEquals(first, retry)
        assertEquals(1, rows.size)
    }

    @Test fun failedInsertCanRetryWithoutCreatingTwoRecords() = runTest {
        val rows = linkedMapOf<String, Flight>()
        var fail = true
        suspend fun commit() = commitNewFlightOnce(flight(), { rows[it] }, { value ->
            if (fail) throw java.io.IOException("disk unavailable")
            rows[value.recordDraftId!!] = value.copy(id = 12L)
            12L
        })
        assertTrue(runCatching { commit() }.isFailure)
        assertTrue(rows.isEmpty())
        fail = false
        assertEquals(12L, commit().id)
        assertEquals(12L, commit().id)
        assertEquals(1, rows.size)
    }

    @Test fun newCommitRejectsBlankSubmissionIdBeforeTouchingStorage() = runTest {
        var writes = 0
        val result = runCatching { commitNewFlightOnce(flight().copy(recordDraftId = " "),
            { null }, { writes++; 1L }) }
        assertTrue(result.isFailure)
        assertEquals(0, writes)
    }
}
