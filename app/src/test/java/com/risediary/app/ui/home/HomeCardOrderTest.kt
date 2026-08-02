package com.risediary.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeCardOrderTest {

    @Test
    fun oldSavedOrder_appendsLengthCardAndOtherNewCards() {
        val result = resolveHomeCardOrder(
            savedOrder = listOf("checkin", "overview", "trend"),
            visibility = emptyMap()
        )

        assertEquals(
            listOf("checkin", "overview", "trend", "length", "achievement"),
            result
        )
    }

    @Test
    fun appendedCard_stillRespectsSavedVisibility() {
        val result = resolveHomeCardOrder(
            savedOrder = listOf("checkin", "overview", "trend"),
            visibility = mapOf("length" to false)
        )

        assertFalse("length" in result)
    }
}
