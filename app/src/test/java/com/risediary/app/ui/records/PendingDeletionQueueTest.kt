package com.risediary.app.ui.records

import com.risediary.app.data.entity.Flight
import org.junit.Assert.assertEquals
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

        assertEquals(listOf(first, second), queue)
    }

    @Test
    fun resolvingFirstDeleteKeepsNextUndoAvailable() {
        val first = flight(1)
        val second = flight(2)

        val remaining = removePendingDeletion(listOf(first, second), first.id)

        assertEquals(listOf(second), remaining)
    }

    @Test
    fun duplicateDeleteDoesNotCreateDuplicateUndoEntries() {
        val original = flight(1)
        val updated = original.copy(moodNote = "updated")

        val queue = enqueuePendingDeletion(listOf(original), updated)

        assertEquals(listOf(updated), queue)
    }

    @Test
    fun staleDeleteCompletionIsRejectedAfterScreenSessionIsCleared() {
        val session = PendingDeletionSession()
        val capturedGeneration = session.capture()

        session.clear()

        assertEquals(false, session.isCurrent(capturedGeneration))
        assertEquals(true, session.isCurrent(session.capture()))
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
