package com.risediary.app.ui.components

import org.junit.Assert.*
import org.junit.Test

class GlassForegroundContrastTest {
    @Test fun darkCoverUsesWhiteEvenInLightTheme() {
        assertTrue(glassForegroundIsLight(IntArray(25) { 0xFF000000.toInt() }, false, 0x24FAFAFA))
    }

    @Test fun brightCoverUsesBlackEvenInDarkTheme() {
        assertFalse(glassForegroundIsLight(IntArray(25) { 0xFFFFFFFF.toInt() }, true, 0x24121212))
    }

    @Test fun midGrayChoosesTheHigherContrastForeground() {
        assertFalse(glassForegroundIsLight(IntArray(25) { 0xFF777777.toInt() }, true))
        assertTrue(glassForegroundIsLight(IntArray(25) { 0xFF707070.toInt() }, false))
    }

    @Test fun transparentOrMissingCaptureRetainsTheLastReadableColor() {
        assertTrue(glassForegroundIsLight(IntArray(25), true))
        assertFalse(glassForegroundIsLight(intArrayOf(), false))
    }

    @Test fun glassSurfaceTintIsIncludedInContrastDecision() {
        val cover = IntArray(25) { 0xFF707070.toInt() }
        assertTrue(glassForegroundIsLight(cover, false))
        assertFalse(glassForegroundIsLight(cover, false, 0x24FAFAFA))
    }

    @Test fun smallChangesNearEqualContrastDoNotFlicker() {
        val nearBoundary = IntArray(25) { 0xFF767676.toInt() }
        assertTrue(glassForegroundIsLight(nearBoundary, true))
        assertFalse(glassForegroundIsLight(nearBoundary, false))
    }
}
