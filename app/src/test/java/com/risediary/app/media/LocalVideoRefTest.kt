package com.risediary.app.media

import org.junit.Assert.*
import org.junit.Test

class LocalVideoRefTest {
    @Test fun localDocumentReferencesAreAcceptedWithoutDependingOnRealPaths() {
        assertTrue(isSupportedLocalVideoUri("content://com.android.providers.media.documents/document/video%3A123"))
        assertTrue(isSupportedLocalVideoUri("content://com.android.externalstorage.documents/document/primary%3AMovies%2Fa.mp4"))
    }

    @Test fun remoteAndUnscopedFileReferencesAreRejected() {
        listOf("", "file:///sdcard/a.mp4", "https://example.com/a.mp4", "content:/a.mp4",
            "content://", "content://authority/a b.mp4", "content://user@provider/video"
        ).forEach { assertFalse(isSupportedLocalVideoUri(it)) }
    }

    @Test fun restoredMetadataMustDescribeOneCompleteLocalAttachment() {
        assertNull(validateLocalVideoFields(null, null, null))
        assertNull(validateLocalVideoFields("content://provider/video/1", "本地视频.mp4", "video/mp4"))
        assertNotNull(validateLocalVideoFields(null, "video.mp4", null))
        assertNotNull(validateLocalVideoFields("content://provider/video/1", null, "video/mp4"))
        assertNotNull(validateLocalVideoFields("https://example.com/a.mp4", "a.mp4", "video/mp4"))
        assertNotNull(validateLocalVideoFields("content://provider/video/1", "  ", "video/mp4"))
        assertNotNull(validateLocalVideoFields("content://provider/video/1", "a.jpg", "image/jpeg"))
    }
}