package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.DataMaintenanceBusyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class FormRecordSaveWorkflowTest {
    @Test
    fun maintenanceBusyAfterPrimaryCommitIsAWarningNotLostIdentity() = runTest {
        val workflow = FormRecordSaveWorkflow(insert = { it.copy(id = 7) }, update = {}, readCurrent = { null })
        val result = workflow.save(flight(), afterInsert = { throw DataMaintenanceBusyException() })
        assertEquals(7L, result.flight.id)
        assertEquals(7L, workflow.persistedFlight!!.id)
        assertEquals(1, result.followUpFailures.size)
    }
    @Test
    fun achievementFailureAfterCommitKeepsSavedIdentityAndDoesNotInsertAgain() = runTest {
        val stored = linkedMapOf<Long, Flight>()
        var nextId = 1L
        val workflow = FormRecordSaveWorkflow(
            insert = { flight -> val id = nextId++; flight.copy(id = id).also { stored[id] = it } },
            update = { stored[it.id] = it },
            readCurrent = { stored[it] }
        )
        val first = workflow.save(flight(), afterInsert = { throw IOException("achievement failed") })
        assertEquals(1L, first.flight.id)
        assertEquals(1L, workflow.persistedFlight!!.id)
        assertEquals(1, first.followUpFailures.size)
        workflow.save(flight().copy(moodNote = "retry"))
        assertEquals(1, stored.size)
        assertEquals("retry", stored.getValue(1).moodNote)
    }

    @Test
    fun failedPrimaryInsertKeepsDraftNewSoRetryCanCommit() = runTest {
        var fail = true
        val stored = mutableListOf<Flight>()
        val workflow = FormRecordSaveWorkflow(
            insert = { value ->
                if (fail) throw IOException("primary write failed")
                stored += value.copy(id = 1)
                value.copy(id = 1)
            },
            update = { throw AssertionError("new record must insert") },
            readCurrent = { null }
        )
        try {
            workflow.save(flight())
            throw AssertionError("Expected primary failure")
        } catch (_: IOException) {
            assertNull(workflow.persistedFlight)
        }
        fail = false
        workflow.save(flight())
        assertEquals(1, stored.size)
        assertNotNull(workflow.persistedFlight)
    }

    @Test
    fun cancelledPostSaveWorkDoesNotEraseCommittedIdentity() = runTest {
        val workflow = FormRecordSaveWorkflow(insert = { it.copy(id = 42) }, update = {}, readCurrent = { null })
        try {
            workflow.save(flight(), afterInsert = { throw CancellationException("page disposed") })
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(42L, workflow.persistedFlight!!.id)
        }
    }

    @Test
    fun reminderFailureStillRunsTimerCleanupAndPreservesAchievementResult() = runTest {
        var timerWasReset = false
        val workflow = FormRecordSaveWorkflow(insert = { it.copy(id = 1) }, update = {}, readCurrent = { null })
        val result = workflow.save(
            flight(),
            afterInsert = { listOf("milestone_1") },
            followUps = listOf(
                { throw IOException("reminder failed") },
                { timerWasReset = true }
            )
        )
        assertEquals(listOf("milestone_1"), result.achievementKeys)
        assertEquals(1, result.followUpFailures.size)
        assertEquals(true, timerWasReset)
    }

    @Test fun editingKeepsTheOriginalGlobalIdentityAndWearableOrigin() = runTest {
        val original = flight().copy(id = 9L, recordSource = "wearable", sourceDeviceId = "band-app-1")
        var current = original
        val workflow = FormRecordSaveWorkflow(insert = { throw AssertionError("edit must not insert") },
            update = { current = it }, readCurrent = { current })
        workflow.loadOriginal(original)
        val result = workflow.save(flight().copy(moodNote = "edited"))
        assertEquals(original.globalId, result.flight.globalId)
        assertEquals(original.recordSource, result.flight.recordSource)
        assertEquals(original.sourceDeviceId, result.flight.sourceDeviceId)
        assertEquals("edited", current.moodNote)
    }

    @Test fun retryAfterPostSaveFailureCannotReplaceTheCommittedIdentity() = runTest {
        var current: Flight? = null
        val workflow = FormRecordSaveWorkflow(insert = { it.copy(id = 4L).also { current = it } },
            update = { current = it }, readCurrent = { current })
        val first = workflow.save(flight(), afterInsert = { throw IOException("follow-up failed") })
        val retried = workflow.save(flight().copy(moodNote = "retry"))
        assertEquals(first.flight.globalId, retried.flight.globalId)
        assertEquals(first.flight.recordSource, retried.flight.recordSource)
    }


    private fun flight() = Flight(
        startTime = 1_000, endTime = 61_000, durationSeconds = 60,
        spurtCount = 3, semenVolumeMl = 6f, ejaculationDistanceCm = null,
        methodTags = "[]", moodNote = "draft", createdAt = 1_000, updatedAt = 61_000
    )
}
