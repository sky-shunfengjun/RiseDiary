package com.risediary.app.ui.records

import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.*
import com.risediary.app.media.*
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.util.LocalCalendarContext
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDetailDeleteUndoTest {
    private val original = Flight(id = 1, startTime = 1_000, endTime = 61_000, durationSeconds = 60,
        spurtCount = null, semenVolumeMl = 2.3f, ejaculationDistanceCm = null,
        methodTags = "[]", moodNote = "keep", recordDraftId = "submission",
        videoUri = "content://videos/1", videoDisplayName = "one.mp4", videoMimeType = "video/mp4")

    private inner class Fixture(scope: TestScope) {
        var record: Flight? = original
        var failDelete = false
        var deleteCalls = 0
        var insertCalls = 0
        var waitForDelete: CompletableDeferred<Unit>? = null
        val gate = DataMaintenanceGate()
        var keptUris = emptySet<String>()
        val files = object : VideoFileAccess {
            override suspend fun acquire(uriString: String, flags: Int) = Result.success(LocalVideoRef(uriString, "one.mp4", "video/mp4"))
            override suspend fun check(video: LocalVideoRef) = VideoAccessState.READABLE
            override suspend fun releaseUnused(referencedUris: Set<String>) { keptUris = referencedUris }
        }
        val repo = object : FlightRepository by proxy(FlightRepository::class.java) {
            override val allFlights = MutableStateFlow(listOf(original))
            override suspend fun getById(id: Long) = record
            override suspend fun delete(flight: Flight) {
                deleteCalls++
                waitForDelete?.await()
                if (failDelete) throw IOException("full")
                record = null
            }
            override suspend fun insert(flight: Flight): Long = error("Undo must not use replacing insert")
            override suspend fun restoreDeleted(flight: Flight) {
                check(record == null)
                insertCalls++; record = flight
            }
        }
        val tags = proxy(TagRepository::class.java) { method -> if (method == "getAllTags") MutableStateFlow(emptyList<com.risediary.app.data.entity.Tag>()) else null }
        val grants = VideoGrantRegistry({ setOfNotNull(record?.videoUri) }, files, gate, scope.backgroundScope)
        private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        private val scheduler = unsafe.allocateInstance(ReminderScheduler::class.java) as ReminderScheduler
        val records = RecordsViewModel(repo, tags, LocalCalendarContext(Clock.systemUTC(), { ZoneId.of("UTC") }, scope.backgroundScope), scheduler, gate, grants)
        val detail = RecordDetailViewModel(repo, scheduler, gate, grants, files, RecordVideoRelinker(repo, grants, gate, Clock.systemUTC()))
    }

    private fun <T : Any> proxy(type: Class<T>, result: (String) -> Any? = { null }): T = requireNotNull(type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> result(method.name) }))

    private suspend fun TestScope.fixture(block: suspend (Fixture) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val f = Fixture(this)
        try { f.detail.load(1); runCurrent(); block(f) }
        finally { f.detail.viewModelScope.cancel(); f.records.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun confirmedDetailDeletionCanRestoreTheWholeRecordAndKeepsVideoPermissionUntilUndo() = runTest {
        fixture { f ->
            f.detail.delete(f.records::deleteForUndo)
            runCurrent()
            assertNull(f.record)
            assertTrue(f.detail.deleted.value)
            assertEquals(1, f.records.pendingDeletions.value.size)
            f.grants.requestCleanup(); runCurrent()
            assertEquals(setOf("content://videos/1"), f.keptUris)
            f.records.undoDelete(original); runCurrent()
            assertEquals(original, f.record)
            assertTrue(f.records.pendingDeletions.value.isEmpty())
            f.records.undoDelete(original); runCurrent()
            assertEquals(1, f.insertCalls)
        }
    }

    @Test fun confirmedListDeletionCanRestoreLegacyRecordWithoutSubmissionId() = runTest {
        fixture { f ->
            val legacy = original.copy(recordDraftId = null, legacySpurtCount = 3, legacyVolumeMl = 1.5f)
            f.record = legacy
            f.records.delete(legacy); runCurrent()
            assertNull(f.record)
            f.records.undoDelete(legacy); runCurrent()
            assertEquals(legacy, f.record)
            assertEquals(1, f.insertCalls)
            assertNull(f.records.writeError.value)
        }
    }

    @Test fun failedDeleteKeepsDetailOpenWithoutAnUndoEntryAndCanRetry() = runTest {
        fixture { f ->
            f.failDelete = true
            f.detail.delete(f.records::deleteForUndo); runCurrent()
            assertEquals(original, f.record)
            assertFalse(f.detail.deleted.value)
            assertNotNull(f.detail.error.value)
            assertTrue(f.records.pendingDeletions.value.isEmpty())
            f.failDelete = false
            f.detail.delete(f.records::deleteForUndo); runCurrent()
            assertTrue(f.detail.deleted.value)
            assertNull(f.detail.error.value)
        }
    }

    @Test fun restoreAfterOpeningDetailCannotDeleteRestoredData() = runTest {
        fixture { f ->
            val restored = original.copy(moodNote = "restored")
            f.gate.maintenance { f.record = restored }; runCurrent()
            f.detail.delete(f.records::deleteForUndo); runCurrent()
            assertEquals(restored, f.record)
            assertEquals(0, f.deleteCalls)
            assertFalse(f.detail.deleted.value)
            assertTrue(f.records.pendingDeletions.value.isEmpty())
        }
    }

    @Test fun detailWaitsForDeletionAndRejectsRepeatedConfirmations() = runTest {
        fixture { f ->
            f.waitForDelete = CompletableDeferred()
            f.detail.delete(f.records::deleteForUndo); runCurrent()
            assertFalse(f.detail.deleted.value)
            assertTrue(f.detail.videoBusy.value)
            f.detail.delete(f.records::deleteForUndo); runCurrent()
            assertEquals(1, f.deleteCalls)
            f.waitForDelete!!.complete(Unit); runCurrent()
            assertTrue(f.detail.deleted.value)
            assertEquals(1, f.records.pendingDeletions.value.size)
        }
    }

    @Test fun rapidRestoreCannotLetUndoReinsertAnOldRecordWhenStateEmissionsConflate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(this)
        try {
            runCurrent()
            f.records.delete(original); runCurrent()
            assertNull(f.record)
            val restored = original.copy(id = 2, moodNote = "restored other data", recordDraftId = "other")
            f.gate.maintenance { f.record = restored }
            // WORKING/IDLE can both arrive before the observer resumes.
            f.records.undoDelete(original); runCurrent()
            assertEquals(restored, f.record)
            assertEquals(0, f.insertCalls)
            assertTrue(f.records.pendingDeletions.value.isEmpty())
        } finally {
            f.detail.viewModelScope.cancel(); f.records.viewModelScope.cancel(); Dispatchers.resetMain()
        }
    }

    @Test fun duplicateListConfirmationCannotReplaceAnInFlightUndoEntryOrLeakItsVideoGrant() = runTest {
        fixture { f ->
            f.waitForDelete = CompletableDeferred()
            val firstAccepted = f.records.delete(original)
            val secondAccepted = f.records.delete(original)
            f.waitForDelete!!.complete(Unit); runCurrent()
            assertTrue(firstAccepted)
            assertFalse(secondAccepted)
            assertEquals(1, f.deleteCalls)
            f.records.finalizeDeletion(original.id); runCurrent()
            f.grants.requestCleanup(); runCurrent()
            assertTrue(f.records.pendingDeletions.value.isEmpty())
            assertEquals(emptySet<String>(), f.keptUris)
        }
    }
}
