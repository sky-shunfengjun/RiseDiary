package com.risediary.app.update

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdateDownloadBoundaryTest {
    @Test fun oldDownloadRecordWithoutAuthorizationStillDeserializes() {
        val release = GitHubRelease("v9.0.0", "release", RELEASES_URL)
        val asset = GitHubAsset(3, "app.apk", "https://github.com/sky-shunfengjun/RiseDiary/releases/download/v9.0.0/app.apk", 1024)
        val old = DownloadRecord(7, release, asset, UpdateChannel.OFFICIAL)
        val value = Json.encodeToString(old)
        assertFalse(value.contains("authorization"))
        assertNull(Json.decodeFromString<DownloadRecord>(value).authorization)
    }

    @Test fun confirmedCompletionNeverCallsRemove() = kotlinx.coroutines.test.runTest {
        var removals = 0
        val outcome = cancelDownloadWithPolicy(query = { CancellationQueryState.COMPLETED }, remove = { removals++; 1 })
        assertEquals(DownloadCancellation.COMPLETED, outcome)
        assertEquals(0, removals)
    }

    @Test fun zeroRemovalChecksWhetherTaskReallyDisappeared() = kotlinx.coroutines.test.runTest {
        var reads = 0
        val outcome = cancelDownloadWithPolicy(query = {
            if (++reads == 1) CancellationQueryState.PRESENT else CancellationQueryState.ABSENT
        }, remove = { 0 })
        assertEquals(DownloadCancellation.CANCELLED, outcome)
        assertEquals(2, reads)
    }

    @Test fun zeroRemovalWithPresentTaskReportsFailureRatherThanSuccess() = kotlinx.coroutines.test.runTest {
        assertTrue(runCatching {
            cancelDownloadWithPolicy(query = { CancellationQueryState.PRESENT }, remove = { 0 })
        }.exceptionOrNull() is UpdateDownloadException)
    }

    @Test fun disappearedTaskIsAnIdempotentCancellation() = kotlinx.coroutines.test.runTest {
        var removals = 0
        assertEquals(DownloadCancellation.CANCELLED,
            cancelDownloadWithPolicy(query = { CancellationQueryState.ABSENT }, remove = { removals++; 0 }))
        assertEquals(0, removals)
    }

    @Test fun queryFailureNeverCallsRemove() = kotlinx.coroutines.test.runTest {
        var removals = 0
        assertTrue(runCatching {
            cancelDownloadWithPolicy(query = { throw java.io.IOException("query") }, remove = { removals++; 1 })
        }.isFailure)
        assertEquals(0, removals)
    }

    @Test fun unknownAndOversizedByteCountsDoNotProduceInvalidProgress() {
        assertNull(downloadPercent(20, -1))
        assertNull(downloadPercent(0, 0))
        assertNull(downloadPercent(-1, 100))
        assertEquals(50, downloadPercent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
        assertEquals(100, downloadPercent(200, 100))
        assertEquals(35, downloadPercent(35, 100))
    }
    @Test fun sha256UsesTheActualBytesIncludingAnEmptyFile() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Of(ByteArrayInputStream(byteArrayOf())))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Of(ByteArrayInputStream("abc".toByteArray())))
    }
    @Test fun savedDownloadRetainsItsVersionChannelAndIntegrityFailure() {
        val asset = GitHubAsset(3, "app.apk", "https://github.com/sky-shunfengjun/RiseDiary/releases/download/v9.0.0/app.apk", 1024, "a".repeat(64))
        val release = GitHubRelease("v9.0.0", "release", RELEASES_URL, assets = listOf(asset))
        val record = DownloadRecord(7, release, asset, UpdateChannel.PROXY_7ED, true,
            authorization = authorizeDownload(release, asset, UpdateSettings(), "v1.1.2"))
        assertEquals(record, Json.decodeFromString<DownloadRecord>(Json.encodeToString(record)))
    }
}
