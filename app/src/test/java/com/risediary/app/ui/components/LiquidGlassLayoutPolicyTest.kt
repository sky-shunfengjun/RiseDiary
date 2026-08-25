package com.risediary.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassLayoutPolicyTest {

    @Test
    fun compact_selector_keeps_the_original_pressed_scale() {
        assertEquals(
            78f / 56f,
            originalLiquidSelectionPressedScale(),
            0.0001f
        )
        assertTrue(originalLiquidSelectionPressedScale() > 38f / 32f)
    }
}
