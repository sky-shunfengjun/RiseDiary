package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormRecordStaleDraftTest {
    @Test
    fun recordReplacedAfterDraftWasLoadedCannotBeOverwritten() = runTest {
        val store = FakeRecords()
        val original = flight(id = 4)
        store.records[4] = original
        val workflow = store.workflow()
        workflow.loadOriginal(original)
        val restored = original.copy(moodNote = "restored record", updatedAt = 90_000)
        store.records[4] = restored
        var followUps = 0

        val result = runCatching {
            workflow.save(original.copy(moodNote = "old draft"), followUps = listOf({ followUps++ }))
        }

        assertTrue("A changed stored snapshot must reject the stale draft", result.exceptionOrNull() is StaleRecordDraftException)
        assertEquals(restored, store.records[4])
        assertEquals(original, workflow.persistedFlight)
        assertEquals(0, store.updateCalls)
        assertEquals(0, followUps)
    }

    @Test
    fun removedRecordCannotBeResurrectedByAnOldEditDraft() = runTest {
        val store = FakeRecords()
        val original = flight(id = 4)
        store.records[4] = original
        val workflow = store.workflow()
        workflow.loadOriginal(original)
        store.records.remove(4)

        val result = runCatching { workflow.save(original.copy(moodNote = "old draft")) }

        assertTrue("A missing record must reject the stale draft", result.exceptionOrNull() is StaleRecordDraftException)
        assertTrue(store.records.isEmpty())
        assertEquals(0, store.updateCalls)
        assertEquals(original, workflow.persistedFlight)
    }

    @Test
    fun ownLastCommittedSnapshotAllowsAnotherSave() = runTest {
        val store = FakeRecords()
        val original = flight(id = 4)
        store.records[4] = original
        val workflow = store.workflow()
        workflow.loadOriginal(original)
        workflow.save(original.copy(moodNote = "first edit", updatedAt = 70_000))
        workflow.save(original.copy(moodNote = "second edit", updatedAt = 80_000))

        assertEquals("second edit", store.records.getValue(4).moodNote)
        assertEquals(store.records[4], workflow.persistedFlight)
        assertEquals(2, store.updateCalls)
    }

    @Test
    fun recordReplacedAfterOwnInsertCannotBeOverwrittenOnRetry() = runTest {
        val store = FakeRecords()
        val workflow = store.workflow()
        workflow.save(flight())
        val committed = workflow.persistedFlight!!
        val restored = committed.copy(moodNote = "restored after insert", updatedAt = 90_000)
        store.records[committed.id] = restored

        val result = runCatching { workflow.save(committed.copy(moodNote = "retry")) }

        assertTrue("A later replacement must also reject a new record's retry", result.exceptionOrNull() is StaleRecordDraftException)
        assertEquals(restored, store.records[committed.id])
        assertEquals(committed, workflow.persistedFlight)
        assertEquals(0, store.updateCalls)
    }

    private class FakeRecords {
        val records = linkedMapOf<Long, Flight>()
        var updateCalls = 0
        private var nextId = 10L
        fun workflow() = FormRecordSaveWorkflow(
            insert = { flight -> val id = nextId++; records[id] = flight.copy(id = id); id },
            update = { updateCalls++; records[it.id] = it },
            readCurrent = { records[it] }
        )
    }

    private fun flight(id: Long = 0) = Flight(
        id = id,
        startTime = 1_000, endTime = 61_000, durationSeconds = 60,
        spurtCount = 3, semenVolumeMl = 6f, ejaculationDistanceCm = null,
        methodTags = "[]", moodNote = "loaded", createdAt = 1_000, updatedAt = 61_000
    )
}