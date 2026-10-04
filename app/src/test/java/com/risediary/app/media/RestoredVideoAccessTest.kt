package com.risediary.app.media

import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RestoredVideoAccessTest {
    private fun flight(id: Long, uri: String?) = Flight(id = id, startTime = 100_000,
        endTime = 112_000, durationSeconds = 8, timingSource = "timer", spurtCount = null,
        semenVolumeMl = 2.3f, ejaculationDistanceCm = null, methodTags = "[]", moodNote = "",
        videoUri = uri, videoDisplayName = uri?.let { "video.mp4" }, videoMimeType = uri?.let { "video/mp4" })

    private class Files(val states: Map<String, VideoAccessState>) : VideoFileAccess {
        val reads = mutableMapOf<String, Int>()
        var failure: Exception? = null
        override suspend fun check(video: LocalVideoRef): VideoAccessState {
            failure?.let { throw it }
            reads[video.uriString] = (reads[video.uriString] ?: 0) + 1
            return states.getValue(video.uriString)
        }
        override suspend fun acquire(uriString: String, flags: Int): Result<LocalVideoRef> = error("Restore must not acquire permission")
        override suspend fun releaseUnused(referencedUris: Set<String>) = error("Restore inspection must not revoke permission")
    }

    @Test fun countsAffectedRecordsButChecksEachSharedUriOnce() = runTest {
        val files = Files(mapOf("content://v/ok" to VideoAccessState.READABLE,
            "content://v/missing" to VideoAccessState.MISSING,
            "content://v/denied" to VideoAccessState.PERMISSION_LOST))
        val records = listOf(flight(1, null), flight(2, "content://v/ok"),
            flight(3, "content://v/missing"), flight(4, "content://v/missing"), flight(5, "content://v/denied"))
        assertEquals(3, files.countUnavailableVideos(records))
        assertEquals(1, files.reads["content://v/missing"])
        assertEquals(8, records.last().durationSeconds)
        assertEquals(112_000L, records.last().endTime)
    }

    @Test fun readableVideosAndLegacyRecordsDoNotNeedRelinking() = runTest {
        val files = Files(mapOf("content://v/ok" to VideoAccessState.READABLE))
        assertEquals(0, files.countUnavailableVideos(listOf(flight(1, null), flight(2, "content://v/ok"))))
    }

    @Test fun providerFailureCountsAsUnavailableWithoutDroppingRecords() = runTest {
        val files = Files(emptyMap()).apply { failure = IllegalStateException("provider died") }
        assertEquals(2, files.countUnavailableVideos(listOf(flight(1, "content://v/a"), flight(2, "content://v/b"))))
    }

    @Test fun invalidAssociationRequiresRelinking() = runTest {
        val files = Files(mapOf("content://v/invalid" to VideoAccessState.INVALID))
        assertEquals(1, files.countUnavailableVideos(listOf(flight(1, "content://v/invalid"))))
    }

    @Test fun cancellationDoesNotBecomeMissingFileResult() = runTest {
        val files = Files(emptyMap()).apply { failure = CancellationException("stop") }
        assertTrue(runCatching { files.countUnavailableVideos(listOf(flight(1, "content://v/a"))) }
            .exceptionOrNull() is CancellationException)
    }
}
