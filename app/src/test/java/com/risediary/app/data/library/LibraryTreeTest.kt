package com.risediary.app.data.library

import org.junit.Assert.*
import org.junit.Test

class LibraryTreeTest {
    private fun asset(id: String, folder: String?, name: String, size: Long = 1, time: Long = 1) =
        LibraryAsset(id, folder, "video", name, name, "video/mp4", size, size * 1000, id, null, time, "ready")
    private val folders = listOf(LibraryFolder("a", null, "旅行", 1), LibraryFolder("b", "a", "海边", 2), LibraryFolder("c", null, "收藏", 3))

    @Test fun globalSearchIncludesDeepPathsAndLiteralCharacters() {
        val tree = LibraryTree(LibrarySnapshot(listOf(asset("v", "b", "海边100%.mp4")), folders))
        val entries = tree.entries(null, "海边", LibrarySort.NEWEST)
        assertEquals(listOf("folder:b", "asset:v"), entries.map { it.key })
        assertEquals("素材库 / 旅行 / 海边", entries.last().path)
        assertEquals(listOf("asset:v"), tree.entries(null, "%", LibrarySort.NAME).map { it.key })
    }

    @Test fun foldersStayFirstAndVideoOrderingIsDeterministicWithLongSizes() {
        val tree = LibraryTree(LibrarySnapshot(listOf(asset("v1", null, "B", 5_000_000_000, 1), asset("v2", null, "A", 2, 2)), folders))
        assertEquals(listOf("folder:c", "folder:a", "asset:v1", "asset:v2"), tree.entries(null, "", LibrarySort.SIZE).map { it.key })
        assertEquals(listOf("asset:v2", "asset:v1"), tree.entries(null, "", LibrarySort.NAME).filter { it.asset != null }.map { it.key })
    }

    @Test fun selectingParentAndDescendantsProcessesEveryAssetOnce() {
        val tree = LibraryTree(LibrarySnapshot(listOf(asset("v", "b", "视频")), folders))
        val selected = tree.selection(setOf("folder:a", "folder:b", "asset:v"))
        assertEquals(setOf("folder:a"), selected.roots)
        assertEquals(setOf("a", "b"), selected.folderIds)
        assertEquals(setOf("v"), selected.assetIds)
    }

    @Test fun moveRejectsCyclesAndResolvesNamesWithoutChangingMediaIdentity() {
        val original = asset("v", "b", "视频.mp4", 5_000_000_000)
        val tree = LibraryTree(LibrarySnapshot(listOf(original, asset("other", "c", "视频.mp4")), folders))
        assertThrows(IllegalArgumentException::class.java) { tree.move(setOf("folder:a"), "b") }
        val moved = tree.move(setOf("asset:v"), "c").assets.single { it.id == "v" }
        assertEquals(original.copy(folderId = "c", name = "视频 (2).mp4"), moved)
        assertEquals(original, tree.move(setOf("asset:v"), "b").assets.single { it.id == "v" })
    }

    @Test fun renameRejectsConflictsAcrossFoldersAndAssetsAndBadNames() {
        val tree = LibraryTree(LibrarySnapshot(listOf(asset("v", null, "视频")), folders))
        assertThrows(IllegalArgumentException::class.java) { tree.rename("asset:v", "旅行") }
        assertThrows(IllegalArgumentException::class.java) { tree.rename("asset:v", "  ") }
        assertEquals("新名字", tree.rename("asset:v", " 新名字 ").assets.single().name)
        assertThrows(IllegalArgumentException::class.java) { tree.createFolder("missing", "新建", "x", 1) }
    }

    @Test fun deepTreeTraversalIsIterativeAndMissingSelectionsFailSafely() {
        val deep = (0..2000).map { LibraryFolder("f$it", if (it == 0) null else "f${it - 1}", "目录$it", it.toLong()) }
        val tree = LibraryTree(LibrarySnapshot(listOf(asset("v", "f2000", "视频")), deep))
        assertEquals(setOf("v"), tree.selection(setOf("folder:f0")).assetIds)
        assertThrows(IllegalArgumentException::class.java) { tree.selection(setOf("asset:missing")) }
    }
}
