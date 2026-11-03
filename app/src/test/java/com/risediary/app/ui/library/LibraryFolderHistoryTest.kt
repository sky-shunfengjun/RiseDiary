package com.risediary.app.ui.library

import org.junit.Assert.*
import org.junit.Test

class LibraryFolderHistoryTest {
    @Test fun openingNestedFoldersReturnsThroughVisitedPagesBeforeExitingSecondaryPage() {
        val history = LibraryFolderHistory(listOf("旅行"))
        history.open("海边")
        history.open("日落")
        assertEquals(listOf("旅行", "海边", "日落"), history.pages)
        assertEquals("日落", history.current)
        assertTrue(history.back())
        assertEquals("海边", history.current)
        assertTrue(history.back())
        assertFalse(history.back())
        assertEquals("旅行", history.current)
    }

    @Test fun reopeningAnAncestorCollapsesHistoryWithoutCreatingCycles() {
        val history = LibraryFolderHistory(listOf("a", "b", "c"))
        history.open("b")
        assertEquals(listOf("a", "b"), history.pages)
        history.open("b")
        assertEquals(2, history.depth)
        val restored = LibraryFolderHistory(history.pages)
        assertTrue(restored.back())
        assertEquals("a", restored.current)
    }

    @Test fun removedChildFallsBackToTheLastValidPageAndRemovedRootExits() {
        val history = LibraryFolderHistory(listOf("a", "b", "c"))
        assertTrue(history.reconcile(setOf("a", "b")))
        assertEquals(listOf("a", "b"), history.pages)
        assertFalse(history.reconcile(setOf("b")))
    }
}
