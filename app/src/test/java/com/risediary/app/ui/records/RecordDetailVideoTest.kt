package com.risediary.app.ui.records

import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.media.*
import com.risediary.app.reminder.ReminderScheduler
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDetailVideoTest {
    private val original = Flight(id = 1, startTime = 100_000, endTime = 112_000, durationSeconds = 8,
        timingSource = "timer", spurtCount = null, semenVolumeMl = 2.3f, ejaculationDistanceCm = null,
        methodTags = "[]", moodNote = "keep", videoUri = "content://videos/old",
        videoDisplayName = "old.mp4", videoMimeType = "video/mp4", recordDraftId = "session")

    private inner class Fixture(scope: TestScope) {
        var record = original
        var failWrite = false
        var failCheck = false
        val gate = DataMaintenanceGate()
        val files = object : VideoFileAccess {
            override suspend fun acquire(uriString: String, flags: Int) =
                Result.success(LocalVideoRef(uriString, "new.mp4", "video/mp4"))
            override suspend fun check(video: LocalVideoRef): VideoAccessState {
                if (failCheck) throw IOException("provider unavailable")
                return if (video.uriString.endsWith("/old")) VideoAccessState.MISSING else VideoAccessState.READABLE
            }
            override suspend fun releaseUnused(referencedUris: Set<String>) = Unit
        }
        val repository = Proxy.newProxyInstance(FlightRepository::class.java.classLoader,
            arrayOf(FlightRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getById" -> record
                "update" -> {
                    if (failWrite) throw IOException("write failed")
                    record = args!![0] as Flight
                    Unit
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as FlightRepository
        val grants = VideoGrantRegistry({ setOfNotNull(record.videoUri) }, files, gate, scope.backgroundScope)
        private val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        private val unusedScheduler = (unsafeField.get(null) as Unsafe).allocateInstance(ReminderScheduler::class.java) as ReminderScheduler
        fun newViewModel(source: FlightRepository = repository) = RecordDetailViewModel(source, unusedScheduler,
            gate, grants, files, RecordVideoRelinker(source, grants, gate, Clock.systemUTC()))
        val vm = newViewModel()
    }

    private suspend fun TestScope.withFixture(block: suspend (Fixture) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture(this)
        try { fixture.vm.load(1); runCurrent(); block(fixture) }
        finally { fixture.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun cancelledPickerRetainsOldAssociationAndRestoresControls() = runTest {
        withFixture { f ->
            assertEquals(VideoAccessState.MISSING, f.vm.videoAccess.value)
            assertTrue(f.vm.beginVideoSelection())
            assertFalse(f.vm.beginVideoSelection())
            f.vm.cancelVideoSelection()
            runCurrent()
            assertEquals(original, f.record)
            assertEquals(original, f.vm.flight.value)
            assertFalse(f.vm.videoBusy.value)
        }
    }

    @Test fun failedSaveRetainsAssociationAndCanRetryWithoutDuplicateResult() = runTest {
        withFixture { f ->
            f.failWrite = true
            assertTrue(f.vm.beginVideoSelection())
            f.vm.selectVideo("content://videos/new", 1)
            runCurrent()
            assertEquals(original, f.record)
            assertNotNull(f.vm.error.value)
            assertFalse(f.vm.videoBusy.value)
            f.failWrite = false
            assertTrue(f.vm.beginVideoSelection())
            f.vm.selectVideo("content://videos/new", 1)
            f.vm.selectVideo("content://videos/unexpected", 1)
            runCurrent()
            assertEquals("content://videos/new", f.record.videoUri)
            assertEquals(VideoAccessState.READABLE, f.vm.videoAccess.value)
            assertEquals("session", f.record.recordDraftId)
            assertNull(f.vm.error.value)
        }
    }

    @Test fun refreshAfterEditingUpdatesVideoAndKeepsPauseDuration() = runTest {
        withFixture { f ->
            f.record = original.copy(videoUri = "content://videos/new", videoDisplayName = "new.mp4")
            f.vm.refresh()
            runCurrent()
            assertEquals("new.mp4", f.vm.flight.value!!.videoDisplayName)
            assertEquals(8, f.vm.flight.value!!.durationSeconds)
            assertEquals(VideoAccessState.READABLE, f.vm.videoAccess.value)
        }
    }

    @Test fun providerCheckFailureLeavesRecordVisibleAndSupportsRetry() = runTest {
        withFixture { f ->
            f.failCheck = true
            f.vm.refresh()
            runCurrent()
            assertEquals(original, f.vm.flight.value)
            assertNotNull(f.vm.error.value)
            assertFalse(f.vm.videoBusy.value)
            f.failCheck = false
            f.vm.refresh()
            runCurrent()
            assertNull(f.vm.error.value)
            assertEquals(VideoAccessState.MISSING, f.vm.videoAccess.value)
        }
    }

    @Test fun lateCancelledReadCannotOverwriteTheRefreshedRecord() = runTest {
        withFixture { f ->
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            var reads = 0
            val repository = object : FlightRepository by f.repository {
                override suspend fun getById(id: Long): Flight? {
                    if (++reads == 1) return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        entered.complete(Unit)
                        release.await()
                        original
                    }
                    return f.record
                }
            }
            val vm = f.newViewModel(repository)
            try {
                vm.load(1)
                entered.await()
                val updated = original.copy(moodNote = "new content")
                f.record = updated
                vm.refresh()
                runCurrent()
                assertEquals(updated, vm.flight.value)
                release.complete(Unit)
                runCurrent()
                assertEquals(updated, vm.flight.value)
            } finally {
                release.complete(Unit)
                vm.viewModelScope.cancel()
            }
        }
    }

    @Test fun pickerResultAfterRestoreCannotModifyTheRestoredRecord() = runTest {
        withFixture { f ->
            assertTrue(f.vm.beginVideoSelection())
            f.gate.maintenance { }
            f.vm.selectVideo("content://videos/new", 1)
            runCurrent()
            assertEquals(original, f.record)
            assertFalse(f.vm.videoBusy.value)
            assertNotNull(f.vm.error.value)
        }
    }
}
