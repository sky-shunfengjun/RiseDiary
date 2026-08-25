package com.risediary.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiseCardPressPolicyTest {

    @Test
    fun enabled_clickable_card_uses_press_feedback() {
        assertTrue(riseCardPressFeedbackEnabled(hasOnClick = true, enabled = true))
    }

    @Test
    fun disabled_or_non_clickable_card_does_not_use_press_feedback() {
        assertFalse(riseCardPressFeedbackEnabled(hasOnClick = false, enabled = true))
        assertFalse(riseCardPressFeedbackEnabled(hasOnClick = true, enabled = false))
    }
}
