package com.risediary.app.ui.records

import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDeletionQueueTest {

    @Test
    fun consecutiveDeletesStayInFirstInFirstOutOrder() {
        val first = flight(1)
        val second = flight(2)

        val queue = enqueuePendingDeletion(
            enqueuePendingDeletion(emptyList(), first),
            second
        )

        assertEquals(listOf(1L, 2L), queue.map { it.flight.id })
    }

    @Test
    fun resolvingFirstDeleteKeepsNextUndoAvailable() {
        val first = flight(1)
        val second = flight(2)

        val queue = enqueuePendingDeletion(
            enqueuePendingDeletion(emptyList(), first),
            second
        )
        val remaining = removePendingDeletion(queue, first.id)

        assertEquals(listOf(second.id), remaining.map { it.flight.id })
    }

    @Test
    fun duplicateDeleteDoesNotCreateDuplicateUndoEntries() {
        val original = flight(1)
        val updated = original.copy(moodNote = "updated")

        val queue = enqueuePendingDeletion(
            enqueuePendingDeletion(emptyList(), original),
            updated
        )

        assertEquals(listOf(updated.id), queue.map { it.flight.id })
        assertEquals(1, queue.size)
        assertEquals("updated", queue.single().flight.moodNote)
    }

    @Test
    fun cancelledEntrySkipsDatabaseDeleteDecisionFlag() {
        val entry = PendingDeletion(flight(1))
        assertFalse(entry.cancelled)
        assertFalse(entry.completed)
        assertFalse(entry.finalized)

        entry.cancelled = true
        assertTrue(entry.cancelled)
    }

    @Test
    fun concurrentDeletesCannotCompleteInReverseOrder() = runTest {
        val operations = PendingDeletionOperations()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val completed = mutableListOf<Long>()

        launch {
            operations.run {
                firstStarted.complete(Unit)
                releaseFirst.await()
                completed += 1L
            }
        }
        firstStarted.await()

        launch {
            operations.run {
                completed += 2L
            }
        }
        runCurrent()

        assertEquals(emptyList<Long>(), completed)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), completed)
    }

    private fun flight(id: Long) = Flight(
        id = id,
        startTime = 1_000L,
        endTime = 61_000L,
        durationSeconds = 60,
        spurtCount = 1,
        semenVolumeMl = 2f,
        ejaculationDistanceCm = null,
        methodTags = "[]",
        moodNote = ""
    )
}
