package com.risediary.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AnchoredBackgroundTest {

    @Test
    fun anchorZeroMapsTheWholeGradientToTheDrawableArea() {
        val (startY, endY) =
            anchoredBackgroundEnds(topInsetPx = 0f, windowHeightPx = 2400f)

        assertEquals(0f, startY)
        assertEquals(2400f, endY)
    }

    @Test
    fun anchoredEndsContinueTheWindowGradientAcrossTheInset() {
        val inset = 108f
        val window = 2400f
        val (startY, endY) =
            anchoredBackgroundEnds(topInsetPx = inset, windowHeightPx = window)

        val localY = 500f
        val pageFraction = (localY - startY) / (endY - startY)
        val windowFraction = (inset + localY) / window

        assertEquals(windowFraction, pageFraction, 0.0001f)
    }
}