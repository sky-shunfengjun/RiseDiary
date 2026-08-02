package com.risediary.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidSegmentedControlTest {

    @Test
    fun compactMask_coversTheRestingLensWithOverscan() {
        val mask = compactLensMaskBounds(
            containerWidth = 144f,
            containerHeight = 38f,
            contentPadding = 3f,
            tabWidth = 69f,
            lensPosition = 0f,
            selectionHeight = 32f,
            scaleX = 1f,
            scaleY = 1f,
            isLtr = true,
            overscan = 1.5f,
        )

        assertEquals(1.5f, mask.left, 0.001f)
        assertEquals(1.5f, mask.top, 0.001f)
        assertEquals(72f, mask.width, 0.001f)
        assertEquals(35f, mask.height, 0.001f)
    }

    @Test
    fun compactMask_tracksTheStretchedLensAtMidpoint() {
        val mask = compactLensMaskBounds(
            containerWidth = 144f,
            containerHeight = 38f,
            contentPadding = 3f,
            tabWidth = 69f,
            lensPosition = 0.5f,
            selectionHeight = 32f,
            scaleX = 1.2f,
            scaleY = 1.25f,
            isLtr = true,
            overscan = 1.5f,
        )

        assertEquals(29.1f, mask.left, 0.001f)
        assertEquals(-2.5f, mask.top, 0.001f)
        assertEquals(85.8f, mask.width, 0.001f)
        assertEquals(43f, mask.height, 0.001f)
    }
}
