package com.risediary.app.media

import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordVideoRelinkerTest {
    private val original = Flight(id = 1, startTime = 100_000, endTime = 112_000,
        durationSeconds = 8, timingSource = "timer", spurtCount = null, semenVolumeMl = 2.3f,
        volumeInputMode = "estimated", predictionMaxTicks = 80, legacySpurtCount = 3,
        legacyVolumeMl = 1.5f, legacyVolumeInputMode = "spurts", ejaculationDistanceCm = 12f,
        methodTags = "[]", moodNote = "keep", createdAt = 99_000, updatedAt = 112_000,
        videoUri = "content://videos/old", videoDisplayName = "old.mp4", videoMimeType = "video/mp4",
        recordDraftId = "saved-session")

    private class Files : VideoFileAccess {
        val granted = mutableSetOf("content://videos/old")
        var failAcquire = false
        override suspend fun acquire(uriString: String, flags: Int): Result<LocalVideoRef> {
            if (failAcquire) return Result.failure(IOException("unavailable"))
            granted += uriString
            return Result.success(LocalVideoRef(uriString, "new.mp4", "video/mp4"))
        }
        override suspend fun check(video: LocalVideoRef) = VideoAccessState.READABLE
        override suspend fun releaseUnused(referencedUris: Set<String>) { granted.retainAll(referencedUris) }
    }

    private inner class Fixture(scope: TestScope) {
        val records = linkedMapOf(1L to original, 2L to original.copy(id = 2))
        val files = Files()
        val gate = DataMaintenanceGate()
        var failWrite = false
        val repository = Proxy.newProxyInstance(FlightRepository::class.java.classLoader,
            arrayOf(FlightRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getById" -> records[args!![0] as Long]
                "update" -> {
                    if (failWrite) throw IOException("disk full")
                    val flight = args!![0] as Flight
                    records[flight.id] = flight
                    Unit
                }
                else -> error("Unexpected repository operation: ${method.name}")
            }
        } as FlightRepository
        val grants = VideoGrantRegistry({ records.values.mapNotNull { it.videoUri }.toSet() },
            files, gate, scope.backgroundScope)
        val relinker = RecordVideoRelinker(repository, grants, gate,
            Clock.fixed(Instant.ofEpochMilli(200_000), ZoneOffset.UTC))
        suspend fun relink(uri: String = "content://videos/new", generation: Long = gate.snapshotGeneration()) =
            relinker.relink(original, generation, "relink", uri, 1)
    }

    @Test fun relinkChangesOnlySelectedRecordVideoAndModificationTime() = runTest {
        val f = Fixture(this)
        val updated = f.relink()
        assertEquals(original.copy(videoUri = "content://videos/new", videoDisplayName = "new.mp4",
            updatedAt = 200_000), updated)
        assertEquals(updated, f.records[1L])
        assertEquals(original.copy(id = 2), f.records[2L])
        runCurrent()
        assertEquals(setOf("content://videos/old", "content://videos/new"), f.files.granted)
    }

    @Test fun selectingSameUriRenewsItsAssociationWithoutLosingPermission() = runTest {
        val f = Fixture(this)
        val updated = f.relink("content://videos/old")
        runCurrent()
        assertEquals("new.mp4", updated.videoDisplayName)
        assertEquals(setOf("content://videos/old"), f.files.granted)
    }

    @Test fun acquisitionFailurePreservesBothRecordsAndOriginalGrant() = runTest {
        val f = Fixture(this).apply { files.failAcquire = true }
        assertTrue(runCatching { f.relink() }.isFailure)
        runCurrent()
        assertEquals(original, f.records[1L])
        assertEquals(original.copy(id = 2), f.records[2L])
        assertEquals(setOf("content://videos/old"), f.files.granted)
    }

    @Test fun writeFailureReleasesNewGrantAndKeepsOldAssociationForRetry() = runTest {
        val f = Fixture(this).apply { failWrite = true }
        assertTrue(runCatching { f.relink() }.isFailure)
        runCurrent()
        assertEquals(original, f.records[1L])
        assertEquals(setOf("content://videos/old"), f.files.granted)
        f.failWrite = false
        assertEquals("content://videos/new", f.relink().videoUri)
    }

    @Test fun modifiedRecordIsNotOverwrittenByPickerResult() = runTest {
        val f = Fixture(this)
        val edited = original.copy(moodNote = "edited")
        f.records[1L] = edited
        assertTrue(runCatching { f.relink() }.isFailure)
        runCurrent()
        assertEquals(edited, f.records[1L])
        assertEquals(setOf("content://videos/old"), f.files.granted)
    }

    @Test fun deletedRecordIsNotRecreatedByPickerResult() = runTest {
        val f = Fixture(this)
        f.records.remove(1L)
        assertTrue(runCatching { f.relink() }.isFailure)
        runCurrent()
        assertFalse(f.records.containsKey(1L))
        assertEquals(original.copy(id = 2), f.records[2L])
    }

    @Test fun restoredIdenticalRecordStillRejectsOldPickerGeneration() = runTest {
        val f = Fixture(this)
        val generation = f.gate.snapshotGeneration()
        f.gate.maintenance { }
        assertTrue(runCatching { f.relink(generation = generation) }.isFailure)
        runCurrent()
        assertEquals(original, f.records[1L])
        assertEquals(setOf("content://videos/old"), f.files.granted)
    }

    @Test fun lastReferenceChangesOnlyAfterSuccessfulWrite() = runTest {
        val f = Fixture(this)
        f.records.remove(2L)
        f.relink()
        runCurrent()
        assertEquals(setOf("content://videos/new"), f.files.granted)
    }
}
