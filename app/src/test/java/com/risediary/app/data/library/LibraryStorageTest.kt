package com.risediary.app.data.library

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LibraryStorageTest {
    private val id = "5fded952-c76b-442f-9945-f90ca463b083"
    @Test fun completeCopyVerifiesBytesBeforePublishing() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val storage = LibraryFileStorage(root)
            val result = storage.copy(id, ByteArrayInputStream("abc".toByteArray()), 3L)
            assertEquals(3L, result.size)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
            assertFalse(storage.media(id).exists())
            storage.commit(id, result)
            assertEquals("abc", storage.media(id).readText())
            assertTrue(File(root, ".nomedia").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun truncatedOrCancelledCopyNeverPublishesAnObject() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val storage = LibraryFileStorage(root)
            assertThrows(IOException::class.java) { storage.copy(id, ByteArrayInputStream(byteArrayOf(1)), 10) }
            assertFalse(storage.media(id).exists())
            assertThrows(InterruptedException::class.java) {
                storage.copy(id, ByteArrayInputStream(ByteArray(300_000)), null, checkCancelled = { throw InterruptedException() })
            }
            assertFalse(storage.media(id).exists())
            assertFalse(storage.staging(id).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun changedStagingIsRejectedAndIdentifiersCannotEscapeRoot() {
        val root = Files.createTempDirectory("library").toFile()
        try {
            val storage = LibraryFileStorage(root)
            val copied = storage.copy(id, ByteArrayInputStream("abc".toByteArray()), null)
            File(storage.staging(id), "media.bin").writeText("def")
            assertThrows(IOException::class.java) { storage.commit(id, copied) }
            assertFalse(storage.media(id).exists())
            assertThrows(IllegalArgumentException::class.java) { storage.media("../outside") }
        } finally { root.deleteRecursively() }
    }
    @Test fun collidingNamesKeepBothVideosAndRetainExtension() {
        assertEquals("movie (3).mp4", uniqueLibraryName("movie.mp4", setOf("movie.mp4", "movie (2).mp4")))
        assertEquals("movie.mp4", uniqueLibraryName("movie.mp4", emptySet()))
    }
}
