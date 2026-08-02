package com.risediary.app.reminder

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeChangeReceiverTest {
    @Test
    fun `known system broadcasts are accepted`() {
        assertTrue(isSupportedTimeChangeAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(isSupportedTimeChangeAction(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `unexpected and missing broadcasts are ignored`() {
        assertFalse(isSupportedTimeChangeAction("com.example.UNEXPECTED"))
        assertFalse(isSupportedTimeChangeAction(null))
    }
}
