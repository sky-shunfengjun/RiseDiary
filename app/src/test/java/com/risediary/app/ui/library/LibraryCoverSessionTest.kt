package com.risediary.app.ui.library

import com.risediary.app.data.library.LibraryCoverFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCoverSessionTest {
    private fun frame(at: Long, automatic: Boolean = false) = LibraryCoverFrame(at, 1000L, byteArrayOf(at.toByte()), automatic)
    @Test fun draggingBeforeTheInitialPreviewCompletesKeepsTheRequestedPosition() = runTest {
        val release = CompletableDeferred<Unit>()
        val session = LibraryCoverSession(backgroundScope, { _, at ->
            if (at == 500L) withContext(NonCancellable) { release.await() }
            frame(at ?: 100L)
        }, { _, _ -> })
        session.open("a", 500L); runCurrent()
        session.seek(400L); advanceTimeBy(201L); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(400L, session.state.value.positionMillis)
    }
    @Test fun draggingClampsTimeAndOnlyTheLastPreviewCanBeSaved() = runTest {
        val reads = mutableListOf<Long?>()
        val saves = mutableListOf<LibraryCoverFrame>()
        val session = LibraryCoverSession(backgroundScope, { _, at -> reads += at; frame(at ?: 100L) }, { _, f -> saves += f })
        session.open("a", 100L); runCurrent()
        session.seek(400L); session.seek(2000L)
        assertTrue(session.state.value.loading)
        session.save(); assertTrue(saves.isEmpty())
        advanceTimeBy(201L); runCurrent()
        assertEquals(listOf(100L, 999L), reads)
        session.save(); session.save(); runCurrent()
        assertEquals(999L, saves.single().positionMillis)
        assertTrue(session.state.value.done)
    }
    @Test fun lateCancelledNativePreviewCannotOverwriteAnotherMaterial() = runTest {
        val release = CompletableDeferred<Unit>()
        val session = LibraryCoverSession(backgroundScope, { id, _ ->
            if (id == "a") withContext(NonCancellable) { release.await() }
            frame(if (id == "a") 100L else 700L)
        }, { _, _ -> })
        session.open("a", 100L); runCurrent()
        session.open("b", 700L); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals("b", session.state.value.assetId)
        assertEquals(700L, session.state.value.frame?.positionMillis)
    }
    @Test fun previewFailureKeepsTheOldImageAndRetryCanRecover() = runTest {
        var failed = false
        val session = LibraryCoverSession(backgroundScope, { _, at -> if (failed) error("decode"); frame(at ?: 100L) }, { _, _ -> })
        session.open("a", 100L); runCurrent()
        failed = true; session.seek(400L); advanceTimeBy(201L); runCurrent()
        assertEquals(100L, session.state.value.frame?.positionMillis)
        assertNotNull(session.state.value.error)
        failed = false; session.retry(); runCurrent()
        assertEquals(400L, session.state.value.frame?.positionMillis)
    }
    @Test fun restoreAutomaticUsesTheAutomaticFrameAndCloseDiscardsLateWork() = runTest {
        val session = LibraryCoverSession(backgroundScope, { _, at -> frame(at ?: 100L, at == null) }, { _, _ -> })
        session.open("a", 800L); runCurrent()
        session.restoreAutomatic(); runCurrent()
        assertTrue(session.state.value.frame?.automatic == true)
        assertEquals(100L, session.state.value.positionMillis)
        session.close(); assertNull(session.state.value.assetId)
    }
    @Test fun failedSaveCanBeRetriedWithoutLosingThePreview() = runTest {
        var fail = true
        val session = LibraryCoverSession(backgroundScope, { _, _ -> frame(200L) }, { _, _ -> if (fail) error("disk full") })
        session.open("a", 200L); runCurrent(); session.save(); runCurrent()
        assertFalse(session.state.value.done)
        assertNotNull(session.state.value.error)
        fail = false; session.save(); runCurrent()
        assertTrue(session.state.value.done)
    }
}
