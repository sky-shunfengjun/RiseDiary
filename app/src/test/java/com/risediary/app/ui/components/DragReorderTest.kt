package com.risediary.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DragReorderTest {

    @Test
    fun movesDownAndPreservesVisualContinuity() {
        val items = mutableListOf("a", "b", "c")

        val offset = reorderByDragOffset(items, "a", 60f, 100f)

        assertEquals(listOf("b", "a", "c"), items)
        assertEquals(-40f, offset, 0.001f)
    }

    @Test
    fun movesUpAndPreservesVisualContinuity() {
        val items = mutableListOf("a", "b", "c")

        val offset = reorderByDragOffset(items, "c", -60f, 100f)

        assertEquals(listOf("a", "c", "b"), items)
        assertEquals(40f, offset, 0.001f)
    }

    @Test
    fun largeDownwardDragMovesToEndAndClampsOverscroll() {
        val items = mutableListOf("a", "b", "c", "d")

        val offset = reorderByDragOffset(items, "a", 1_000f, 100f)

        assertEquals(listOf("b", "c", "d", "a"), items)
        assertEquals(50f, offset, 0.001f)
    }

    @Test
    fun largeUpwardDragMovesToStartAndClampsOverscroll() {
        val items = mutableListOf("a", "b", "c", "d")

        val offset = reorderByDragOffset(items, "d", -1_000f, 100f)

        assertEquals(listOf("d", "a", "b", "c"), items)
        assertEquals(-50f, offset, 0.001f)
    }

    @Test
    fun singleItemOnlyKeepsBoundedElasticOffset() {
        val items = mutableListOf("a")

        val down = reorderByDragOffset(items, "a", 500f, 80f)
        val up = reorderByDragOffset(items, "a", -500f, 80f)

        assertEquals(listOf("a"), items)
        assertEquals(40f, down, 0.001f)
        assertEquals(-40f, up, 0.001f)
    }

    @Test
    fun missingItemDoesNotChangeOrder() {
        val items = mutableListOf("a", "b")

        val offset = reorderByDragOffset(items, "missing", 60f, 100f)

        assertEquals(listOf("a", "b"), items)
        assertEquals(0f, offset, 0.001f)
    }

    @Test
    fun invalidExtentDoesNotChangeOrder() {
        val items = mutableListOf("a", "b")

        val offset = reorderByDragOffset(items, "a", 60f, 0f)

        assertEquals(listOf("a", "b"), items)
        assertEquals(0f, offset, 0.001f)
    }

    @Test
    fun emptyListIsSafe() {
        val items = mutableListOf<String>()

        val offset = reorderByDragOffset(items, "a", 60f, 100f)

        assertEquals(emptyList<String>(), items)
        assertEquals(0f, offset, 0.001f)
    }
}
