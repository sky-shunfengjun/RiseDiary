package com.risediary.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagRenameTest {
    @Test
    fun `rename updates exact historical tag references`() {
        assertEquals(
            listOf("新的名字", "玩具"),
            renameTagNames(listOf("旧的名字", "玩具"), "旧的名字", "新的名字")
        )
    }

    @Test
    fun `rename does not change partial matches`() {
        assertNull(renameTagNames(listOf("旧的名字加长"), "旧的名字", "新的名字"))
    }
}
