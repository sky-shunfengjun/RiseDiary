package com.risediary.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusTest {

    @Test
    fun emptyDay_isPresentedAsReadyToRecord() {
        assertFalse(TodayStatus(0).hasRecords)
    }

    @Test
    fun recordedDay_onlyExposesItsPersonalCount() {
        val status = TodayStatus(3)

        assertTrue(status.hasRecords)
        assertEquals(3, status.count)
    }
}
