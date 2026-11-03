package com.risediary.app.data.library

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.media.TestVideoProvider
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LibraryCoverExtractorTest {
    @Test fun boundedFramesAndAutomaticRestoreNeverModifyOriginalVideoBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("cover-extractor-", ".mp4", context.cacheDir)
        try {
            context.contentResolver.openInputStream(TestVideoProvider.READABLE)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val before = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            val manual = extractLibraryCover(file, Long.MAX_VALUE)
            val automatic = extractLibraryCover(file, null)
            assertTrue(manual.positionMillis < manual.durationMillis)
            assertTrue(automatic.automatic)
            val image = BitmapFactory.decodeByteArray(manual.jpeg, 0, manual.jpeg.size)
            try { assertNotNull(image); assertTrue(image.width <= 1024 && image.height <= 1024) } finally { image?.recycle() }
            assertArrayEquals(before, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
        } finally { file.delete() }
    }
}
