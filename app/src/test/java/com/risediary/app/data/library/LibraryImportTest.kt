package com.risediary.app.data.library

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibraryImportTest {
    @Test fun committedItemCanRecoverEvenIfOriginalSourceIsNowUnavailable() = runTest {
        val root = Files.createTempDirectory("recover-import").toFile()
        try {
            val index = TestIndex()
            val processor = LibraryImportProcessor(LibraryFileStorage(root), index) { LibraryVideoInfo(1000, "video/mp4") }
            val item = item("saved.mp4")
            processor.import(item, ByteArrayInputStream("abc".toByteArray()), 3)
            val result = processor.importSource(item, source = { throw java.io.FileNotFoundException() })
            assertEquals("done", result.status)
            assertEquals(1, index.items.size)
            assertEquals("abc", LibraryFileStorage(root).media(item.id).readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun duplicateContentIsSkippedAndFailedVideoDoesNotEnterIndex() = runTest {
        val root = Files.createTempDirectory("imports").toFile()
        try {
            val storage = LibraryFileStorage(root)
            val index = TestIndex()
            val processor = LibraryImportProcessor(storage, index) { file ->
                if (file.readText() == "invalid") error("不是有效视频")
                LibraryVideoInfo(1000, "video/mp4")
            }
            val first = processor.import(item("a.mp4"), ByteArrayInputStream("abc".toByteArray()), 3)
            val second = processor.import(item("b.mp4"), ByteArrayInputStream("abc".toByteArray()), 3)
            assertEquals("done", first.status)
            assertEquals("duplicate", second.status)
            assertEquals(1, index.items.size)
            assertEquals("a.mp4", second.detail)
            val bad = item("invalid.mp4")
            try { processor.import(bad, ByteArrayInputStream("invalid".toByteArray()), 7); fail() } catch (_: IllegalStateException) { }
            assertEquals(1, index.items.size)
            assertFalse(storage.staging(bad.id).exists())
            assertFalse(storage.media(bad.id).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun restartRetainsCompletedItemsAndOffersOnlyUnfinishedItemsForRetry() {
        val root = Files.createTempDirectory("journal").toFile()
        try {
            val journal = LibraryTransferJournal(File(root, "transfer.json"))
            journal.write(LibraryTransferState(running = true, items = listOf(
                item("done").copy(status = "done"), item("retry").copy(status = "failed"), item("waiting"))))
            val restored = LibraryTransferJournal(File(root, "transfer.json")).read()
            assertFalse(restored.running)
            assertEquals(1, restored.completed)
            assertEquals(listOf("retry", "waiting"), restored.remaining.map { it.name })
            assertTrue(restored.message.isNotBlank())
        } finally { root.deleteRecursively() }
    }
    private fun item(name: String) = LibraryImportItem(java.util.UUID.randomUUID().toString(), "content://source/video", name)
    private class TestIndex : LibraryAssetIndex {
        val items = mutableListOf<LibraryAsset>()
        override suspend fun asset(id: String) = items.find { it.id == id }
        override suspend fun byHash(hash: String) = items.find { it.sha256 == hash }
        override suspend fun names(folderId: String?) = items.filter { it.folderId == folderId }.map { it.name }.toSet()
        override suspend fun publish(asset: LibraryAsset) { items.add(asset) }
    }
}
