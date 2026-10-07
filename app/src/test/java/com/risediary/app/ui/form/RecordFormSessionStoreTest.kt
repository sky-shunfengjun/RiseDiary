package com.risediary.app.ui.form

import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.service.TimerKind
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import org.junit.Assert.*
import org.junit.Test

class RecordFormSessionStoreTest {
    private val video = LocalVideoRef("content://videos/document/7", "local.mp4", "video/mp4")
    private fun finished() = TimerSession(status = TimerStatus.FINISHED, sessionId = "timer-7",
        startedAtEpochMillis = 100_000L, endedAtEpochMillis = 120_000L,
        elapsedMillis = 8_000L, kind = TimerKind.VIDEO, video = VideoPlaybackSnapshot(video))

    @Test fun timerHandoffKeepsWallEndSeparateFromElapsedDuration() {
        val store = RecordFormSessionStore()
        val form = store.createFromTimer(finished(), 80)
        assertEquals(100_000L, form.startTime)
        assertEquals(120_000L, form.endTime)
        assertEquals(8, form.durationSeconds)
        assertEquals("timer", form.timingSource)
        assertEquals("timer-7", form.submissionId)
        assertEquals(video, form.video)
        assertTrue(store.hasUnsavedContent(form.formId))
    }

    @Test fun duplicateHandoffReturnsTheCurrentInputsInsteadOfStartingAnotherForm() {
        val store = RecordFormSessionStore()
        val first = store.createFromTimer(finished(), 80)
        store.update(first.copy(moodNote = "current input"))
        val again = store.createFromTimer(finished(), 150)
        assertEquals(first.formId, again.formId)
        assertEquals("current input", again.moodNote)
        assertEquals(80, again.quantity.predictionMaxTicks)
    }

    @Test fun bothQuantityInputsAndRemovedVideoStayInMemoryDuringThisUse() {
        val store = RecordFormSessionStore()
        val form = store.createFromTimer(finished(), 80)
        store.update(form.copy(quantity = QuantityDraftSnapshot("estimated", 23, "9.5", 80, true),
            video = null, moodNote = "note", methodTags = listOf("manual")))
        val current = requireNotNull(store.get(form.formId))
        assertEquals(23, current.quantity.estimatedTicks)
        assertEquals("9.5", current.quantity.manualText)
        assertNull(current.video)
        assertEquals(emptySet<String>(), store.videoUris())
    }

    @Test fun aFreshProcessCannotRecoverThePreviousForm() {
        val firstProcess = RecordFormSessionStore()
        val form = firstProcess.createManual(100_000L, 80)
        firstProcess.update(form.copy(moodNote = "unsaved"))
        assertNull(RecordFormSessionStore().get(form.formId))
        assertTrue(RecordFormSessionStore().videoUris().isEmpty())
    }

    @Test fun confirmedDiscardRemovesTheFormAndItsVideoReference() {
        val store = RecordFormSessionStore()
        val form = store.createFromTimer(finished(), 80)
        assertEquals(setOf(video.uriString), store.videoUris())
        store.discard(form.formId)
        assertNull(store.get(form.formId))
        assertTrue(store.videoUris().isEmpty())
        assertTrue(runCatching { store.update(form) }.isFailure)
    }

    @Test fun untouchedManualFormDoesNotAskToDiscardButModifiedContentDoes() {
        val store = RecordFormSessionStore()
        val form = store.createManual(100_000L, 80)
        assertFalse(store.hasUnsavedContent(form.formId))
        store.update(form.copy(moodNote = "changed"))
        assertTrue(store.hasUnsavedContent(form.formId))
        store.update(form)
        assertFalse(store.hasUnsavedContent(form.formId))
    }

    @Test fun activeTimerCannotBeConsumedAsARecord() {
        val store = RecordFormSessionStore()
        assertTrue(runCatching {
            store.createFromTimer(finished().copy(status = TimerStatus.PAUSED), 80)
        }.isFailure)
    }

    @Test fun aFormCannotWriteAfterDataReplacement() = kotlinx.coroutines.test.runTest {
        val gate = com.risediary.app.data.DataMaintenanceGate()
        val store = RecordFormSessionStore(gate)
        val form = store.createManual(100_000L, 80)
        gate.maintenance { }
        assertTrue(runCatching { gate.write { gate.requireGeneration(form.dataGeneration) } }.isFailure)
        val fresh = store.createManual(100_000L, 80)
        gate.write { gate.requireGeneration(fresh.dataGeneration) }
    }

    @Test fun formUpdatesCannotChangeTheSubmissionIdentity() {
        val store = RecordFormSessionStore()
        val form = store.createManual(100_000L, 80)
        assertTrue(runCatching { store.update(form.copy(submissionId = "another")) }.isFailure)
        assertEquals(form.submissionId, store.get(form.formId)?.submissionId)
    }
}
