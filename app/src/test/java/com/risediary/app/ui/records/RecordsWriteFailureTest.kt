package com.risediary.app.ui.records

import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.TagRepository
import com.risediary.app.media.*
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.util.LocalCalendarContext
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsWriteFailureTest {
    private val original = Flight(id = 9, startTime = 1000, endTime = 2000, durationSeconds = 1,
        spurtCount = null, semenVolumeMl = 1f, ejaculationDistanceCm = null, methodTags = "[]", moodNote = "",
        videoUri = "content://video/one", videoDisplayName = "one.mp4", videoMimeType = "video/mp4")

    @Test fun failedDeleteNeverReportsSuccessAndRetryCommitsOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture()
        try {
            fixture.failDelete = true
            var successes = 0
            assertTrue(fixture.vm.delete(original) { successes++ })
            assertFalse(fixture.vm.delete(original) { successes++ })
            runCurrent()
            assertEquals(original, fixture.row)
            assertEquals(0, successes)
            assertTrue(fixture.vm.pendingDeletions.value.isEmpty())
            assertNotNull(fixture.vm.writeError.value)
            fixture.failDelete = false
            fixture.vm.retryWrite(); fixture.vm.retryWrite(); runCurrent()
            assertNull(fixture.row)
            assertEquals(1, successes)
            assertTrue(fixture.vm.pendingDeletions.value.single().completed)
        } finally { fixture.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun failedUndoRetainsSnapshotAndVideoGrantUntilRetrySucceeds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture()
        try {
            fixture.vm.delete(original); runCurrent()
            fixture.failInsert = true
            fixture.vm.undoDelete(original); runCurrent()
            assertNull(fixture.row)
            assertEquals(original, fixture.vm.pendingDeletions.value.single().flight)
            fixture.grants.requestCleanup(); runCurrent()
            assertTrue(original.videoUri in fixture.retainedUris)
            fixture.failInsert = false
            fixture.vm.retryWrite(); runCurrent()
            assertEquals(original, fixture.row)
            assertTrue(fixture.vm.pendingDeletions.value.isEmpty())
        } finally { fixture.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun deletionRetryCannotDeleteDataRestoredAfterTheFailedAttempt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture()
        try {
            fixture.failDelete = true
            fixture.vm.delete(original); runCurrent()
            fixture.gate.maintenance { fixture.row = original }
            fixture.failDelete = false
            fixture.vm.retryWrite(); runCurrent()
            assertEquals(original, fixture.row)
            assertTrue(fixture.vm.pendingDeletions.value.isEmpty())
        } finally { fixture.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun undoDoesNotReplaceALaterRowUsingTheSameLocalId() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = fixture()
        try {
            fixture.vm.delete(original); runCurrent()
            val later = original.copy(globalId = "different-record", moodNote = "later")
            fixture.row = later
            fixture.vm.undoDelete(original); runCurrent()
            assertEquals(later, fixture.row)
            assertNotNull(fixture.vm.writeError.value)
        } finally { fixture.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private inner class Fixture {
        var row: Flight? = original
        var failDelete = false; var failInsert = false
        var retainedUris = emptySet<String>()
        val gate = DataMaintenanceGate()
        lateinit var vm: RecordsViewModel
        lateinit var grants: VideoGrantRegistry
    }
    private fun TestScope.fixture(): Fixture {
        val fixture = Fixture()
        val flights = Proxy.newProxyInstance(FlightRepository::class.java.classLoader, arrayOf(FlightRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getAllFlights" -> flowOf(listOf(original))
                "getById" -> fixture.row
                "delete" -> { if (fixture.failDelete) throw IOException("delete failed"); fixture.row = null; Unit }
                "restoreDeleted" -> { if (fixture.failInsert) throw IOException("insert failed"); fixture.row = original; Unit }
                else -> throw AssertionError("Unexpected call ${method.name}")
            }
        } as FlightRepository
        val tags = Proxy.newProxyInstance(TagRepository::class.java.classLoader, arrayOf(TagRepository::class.java)) { _, method, _ ->
            if (method.name == "getAllTags") flowOf(emptyList<Any>()) else throw AssertionError(method.name)
        } as TagRepository
        fixture.grants = VideoGrantRegistry({ setOfNotNull(fixture.row?.videoUri) }, object : VideoFileAccess {
            override suspend fun acquire(uriString: String, flags: Int) = Result.failure<LocalVideoRef>(AssertionError("unused"))
            override suspend fun check(video: LocalVideoRef) = VideoAccessState.READABLE
            override suspend fun releaseUnused(referencedUris: Set<String>) { fixture.retainedUris = referencedUris }
        }, fixture.gate, backgroundScope)
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val scheduler = unsafe.allocateInstance(ReminderScheduler::class.java) as ReminderScheduler
        fixture.vm = RecordsViewModel(flights, tags, LocalCalendarContext(Clock.systemUTC()), scheduler, fixture.gate, fixture.grants)
        return fixture
    }
}
