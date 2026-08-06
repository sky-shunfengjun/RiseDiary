package com.risediary.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiquidGlassControlsTest {
    @Test
    fun dragBackToCurrentStateDoesNotRequestChange() {
        assertNull(requestedToggleState(currentChecked = false, fraction = 0f))
        assertNull(requestedToggleState(currentChecked = true, fraction = 1f))
    }

    @Test
    fun dragToDifferentStateRequestsTheNewValue() {
        assertEquals(
            true,
            requestedToggleState(currentChecked = false, fraction = 1f)
        )
        assertEquals(
            false,
            requestedToggleState(currentChecked = true, fraction = 0f)
        )
    }

    @Test
    fun dragThresholdUsesHalfAsChecked() {
        assertNull(requestedToggleState(currentChecked = false, fraction = 0.49f))
        assertEquals(true, requestedToggleState(currentChecked = false, fraction = 0.5f))
        assertEquals(true, requestedToggleState(currentChecked = false, fraction = 0.51f))
        assertEquals(false, requestedToggleState(currentChecked = true, fraction = 0.49f))
        assertNull(requestedToggleState(currentChecked = true, fraction = 0.5f))
        assertNull(requestedToggleState(currentChecked = true, fraction = 0.51f))
    }
}
