package com.risediary.app.ui.settings

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailVideoSettingsEditorTest {
    @Test fun toggleDoesNotChangeBeforeSuccessfulCommitAndRejectsDuplicateWrites() = runTest {
        val value = MutableStateFlow(false)
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val editor = DetailVideoSettingsEditor(backgroundScope, value, { hidden ->
            writes++
            release.await()
            value.value = hidden
        }, "read failed", "save failed")
        runCurrent()
        editor.setHidden(true)
        runCurrent()
        assertTrue(editor.state.value.saving)
        assertFalse(editor.state.value.hiddenByDefault)
        editor.setHidden(false)
        assertEquals(1, writes)
        release.complete(Unit)
        runCurrent()
        assertTrue(editor.state.value.hiddenByDefault)
        assertFalse(editor.state.value.saving)
    }

    @Test fun failedSaveKeepsCommittedValueAndRetryWritesTheRequestedValue() = runTest {
        val value = MutableStateFlow(true)
        var fail = true
        val editor = DetailVideoSettingsEditor(backgroundScope, value, { hidden ->
            if (fail) throw IOException("full")
            value.value = hidden
        }, "read failed", "save failed")
        runCurrent()
        editor.setHidden(false)
        runCurrent()
        assertTrue(editor.state.value.hiddenByDefault)
        assertEquals("save failed", editor.state.value.error)
        assertFalse(editor.state.value.saving)
        fail = false
        editor.retry()
        runCurrent()
        assertFalse(editor.state.value.hiddenByDefault)
        assertNull(editor.state.value.error)
    }

    @Test fun readFailureDisablesToggleAndRetryReadsActualPrivacyValue() = runTest {
        var fail = true
        var writes = 0
        val editor = DetailVideoSettingsEditor(backgroundScope, flow {
            if (fail) throw IOException("unreadable")
            emit(true)
        }, { writes++ }, "read failed", "save failed")
        runCurrent()
        assertFalse(editor.state.value.ready)
        assertEquals("read failed", editor.state.value.error)
        editor.setHidden(false)
        runCurrent()
        assertEquals(0, writes)
        fail = false
        editor.retry()
        runCurrent()
        assertTrue(editor.state.value.ready)
        assertTrue(editor.state.value.hiddenByDefault)
        assertNull(editor.state.value.error)
    }

    @Test fun simultaneousReadAndWriteFailureRetriesReadingBeforeWritingPendingChoice() = runTest {
        val value = MutableStateFlow(true)
        var failRead = false
        var failWrite = true
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val editor = DetailVideoSettingsEditor(backgroundScope, flow {
            if (failRead) throw IOException("read")
            events += "read"
            value.collect { hidden ->
                if (failRead) throw IOException("read")
                emit(hidden)
            }
        }, { hidden ->
            events += "write:$hidden"
            if (failWrite) { release.await(); throw IOException("write") }
            value.value = hidden
        }, "read failed", "save failed")
        runCurrent()
        editor.setHidden(false)
        runCurrent()
        failRead = true
        value.value = false
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertFalse(editor.state.value.ready)
        failRead = false
        failWrite = false
        events.clear()
        editor.retry()
        runCurrent()
        assertEquals(listOf("read", "write:false"), events)
        assertTrue(editor.state.value.ready)
        assertFalse(editor.state.value.hiddenByDefault)
        assertNull(editor.state.value.error)
    }

    @Test fun finishingAWriteCannotOverwriteANewerRestoredPreference() = runTest {
        val value = MutableStateFlow(false)
        val editor = DetailVideoSettingsEditor(backgroundScope, value, { hidden ->
            value.value = hidden
            yield()
            value.value = false // A backup restore commits after the toggle, before its callback returns.
            yield()
        }, "read failed", "save failed")
        runCurrent()
        editor.setHidden(true)
        runCurrent()
        assertFalse(value.value)
        assertFalse(editor.state.value.hiddenByDefault)
        assertTrue(editor.state.value.ready)
        assertFalse(editor.state.value.saving)
    }
}
