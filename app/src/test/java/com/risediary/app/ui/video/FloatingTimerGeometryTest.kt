package com.risediary.app.ui.video

import org.junit.Assert.*
import org.junit.Test

class FloatingTimerGeometryTest {
    @Test fun firstPositionIsTopLeft() {
        assertEquals(FloatingPoint(0f, 0f), floatingTimerOffset(FloatingTimerPosition(), 800f, 400f, 200f, 70f))
    }
    @Test fun dragClampsAtAllEdges() {
        val start = FloatingTimerPosition(0f, 1f)
        val moved = moveFloatingTimer(start, 9999f, -9999f, 800f, 400f, 200f, 70f)
        assertEquals(FloatingTimerPosition(1f, 0f), moved)
        assertEquals(start, moveFloatingTimer(moved, -9999f, 9999f, 800f, 400f, 200f, 70f))
    }
    @Test fun rotationKeepsRelativePosition() {
        val relative = FloatingTimerPosition(0.75f, 0.25f)
        assertEquals(FloatingPoint(450f, 82.5f), floatingTimerOffset(relative, 800f, 400f, 200f, 70f))
        assertEquals(FloatingPoint(150f, 182.5f), floatingTimerOffset(relative, 400f, 800f, 200f, 70f))
    }
    @Test fun collapsedViewportDoesNotDestroySavedPosition() {
        val relative = FloatingTimerPosition(0.75f, 0.25f)
        assertEquals(relative, moveFloatingTimer(relative, 100f, 100f, 0f, 0f, 200f, 70f))
        assertEquals(FloatingPoint(0f, 0f), floatingTimerOffset(relative, 0f, 0f, 200f, 70f))
    }
    @Test fun panelFlipsAndStaysInsideViewport() {
        assertEquals(FloatingPoint(480f, 270f), floatingTimerPanelOffset(FloatingPoint(600f, 330f), 70f, 320f, 130f, 800f, 400f))
        assertEquals(FloatingPoint(0f, 0f), floatingTimerPanelOffset(FloatingPoint(0f, 0f), 70f, 320f, 130f, 800f, 400f))
    }
    @Test fun oversizedPanelIsPinnedToOrigin() {
        assertEquals(FloatingPoint(0f, 0f), floatingTimerPanelOffset(FloatingPoint(20f, 20f), 70f, 1000f, 900f, 100f, 100f))
    }
    @Test fun transparentCapsuleWrapsDigitsAndCanReachTheRightEdge() {
        val capsuleWidth = floatingTimerCapsuleWidth(800f, 1f)
        val digitWidth = 258f * 22f / 52f
        assertEquals("Only the two 8dp touch insets may surround the visible clock",
            digitWidth + 16f, capsuleWidth, 0.01f)
        val edge = floatingTimerOffset(FloatingTimerPosition(1f, 0f), 800f, 400f, capsuleWidth, 60f)
        assertEquals(792f, edge.x + 8f + digitWidth, 0.01f)
    }
    @Test fun capsuleGrowsWithFontScaleWithoutAddingInvisibleTravelLimits() {
        assertEquals(258f * 44f / 52f + 16f, floatingTimerCapsuleWidth(800f, 2f), 0.01f)
    }
    @Test fun narrowAndZeroViewportsKeepTheClockInsideTheAvailableArea() {
        assertEquals(88f, floatingTimerCapsuleWidth(160f, 2f), 0.01f)
        assertEquals(0f, floatingTimerCapsuleWidth(0f, 1f), 0f)
    }
}
