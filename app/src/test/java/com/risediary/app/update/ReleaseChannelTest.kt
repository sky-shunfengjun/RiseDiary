package com.risediary.app.update

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReleaseChannelTest {
    private fun payload(id: Long, time: String, beta: Boolean = false, draft: Boolean = false) = """{
        "id":$id,"published_at":"$time","draft":$draft,"prerelease":$beta,
        "tag_name":"v1.0.0${if (beta) "-beta$id" else ""}","assets":[]
    }"""
    @Test fun previewIncludesBothKindsExcludesDraftsAndUsesLatestPublishedPage() = runTest {
        val requested = mutableListOf<Int>()
        val selected = fetchNewestPublishedRelease { page ->
            requested.add(page)
            ReleasePage(parseReleaseList(if (page == 1) "[${payload(99,"2026-09-30T00:00:00Z")},${payload(999,"2026-10-03T00:00:00Z",draft=true)}]"
                else "[${payload(100,"2026-10-01T00:00:00Z",beta=true)}]"), page == 1)
        }
        assertEquals(listOf(1, 2), requested)
        assertEquals(100L, selected.id)
        assertTrue(selected.prerelease)
        assertNull(selectReleaseApk(selected)) // No fallback to an older release's APK.
    }
    @Test fun stableStillRejectsPrereleaseAndDraft() {
        for (p in listOf(payload(1,"2026-10-01T00:00:00Z",beta=true),payload(2,"2026-10-01T00:00:00Z",draft=true)))
            assertTrue(runCatching { parseReleaseResponse(p) }.isFailure)
    }
    @Test fun laterPageFailureEmptyListAndInvalidMetadataFailTheWholeCheck() = runTest {
        assertTrue(runCatching { fetchNewestPublishedRelease { ReleasePage(emptyList(), false) } }.isFailure)
        assertTrue(runCatching { fetchNewestPublishedRelease { page ->
            if (page == 2) throw java.io.IOException("GitHub HTTP 403")
            ReleasePage(parseReleaseList("[${payload(1,"2026-10-01T00:00:00Z")}]"),true)
        } }.isFailure)
        assertTrue(runCatching { parseReleaseList("[${payload(1,"invalid")}]") }.isFailure)
    }
    @Test fun newReleaseFieldsAreOptionalInPreviouslySavedDownloadRecords() {
        val old = """{"id":7,"release":{"tagName":"v1.0.0","name":"old","releaseUrl":"$RELEASES_URL"},
            "asset":{"id":2,"name":"app.apk","downloadUrl":"https://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.0.0/app.apk","size":1},"channel":"OFFICIAL"}"""
        val record = Json.decodeFromString<DownloadRecord>(old)
        assertEquals(0L,record.release.id)
        assertFalse(record.release.prerelease)
        assertNull(record.release.publishedAt)
    }
}