package com.risediary.app.ui.onboarding.original

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class OriginalMotionCurvesTest {
    @Test fun revealCoversAllCornersEvenWithInsets() {
        val g = revealGeometry(Rect(145f, 680f, 215f, 750f), Size(360f, 800f))
        assertTrue(g.isUsable)
        assertEquals(180f, g.centerX, 0f)
        assertEquals(715f, g.centerY, 0f)
        assertEquals(35f, g.initialRadius, 0.01f)
        listOf(Offset.Zero, Offset(360f, 0f), Offset(0f, 800f), Offset(360f, 800f)).forEach { corner ->
            assertTrue(hypot(corner.x - g.centerX, corner.y - g.centerY) <= g.finalRadius + 0.01f)
        }
    }
    @Test fun invalidViewportDoesNotLaunchUnfinishableReveal() {
        assertFalse(revealGeometry(Rect(10f, 10f, 80f, 80f), Size.Zero).isUsable)
        assertFalse(revealGeometry(Rect(10f, 10f, 80f, 80f), Size(Float.NaN, 800f)).isUsable)
        assertFalse(revealGeometry(Rect.Zero, Size(360f, 800f)).isUsable)
    }
    @Test fun rotatedGeometryUsesCurrentViewportRatherThanOldRadius() {
        val g = revealGeometry(Rect(680f, 145f, 750f, 215f), Size(800f, 360f))
        assertEquals(715f, g.centerX, 0f)
        assertEquals(180f, g.centerY, 0f)
        assertTrue(g.finalRadius > 730f)
    }
    @Test fun logoHasOriginalTwoStageScalingAndDelayedFade() {
        assertEquals(0.5f, OriginalMotionCurves.logoScale(0f), 0f)
        assertEquals(0.95f, OriginalMotionCurves.logoScale(440f), 0.001f)
        assertEquals(1f, OriginalMotionCurves.logoScale(1140f), 0f)
        assertEquals(0f, OriginalMotionCurves.logoAlpha(59f), 0f)
        assertEquals(1f, OriginalMotionCurves.logoAlpha(360f), 0f)
    }
    @Test fun arrowWaitsForOriginalDelayAndThenBecomesTouchable() {
        assertEquals(0f, OriginalMotionCurves.arrowAlpha(1339f), 0f)
        assertEquals(0.9f, OriginalMotionCurves.arrowScale(1339f), 0f)
        assertEquals(1f, OriginalMotionCurves.arrowAlpha(1790f), 0f)
        assertEquals(1f, OriginalMotionCurves.arrowScale(1790f), 0f)
    }
    @Test fun completedAdmissionKeepsFlowingWithOriginalPingPong() {
        assertEquals(2f, OriginalMotionCurves.glowSeconds(2000f), 0f)
        assertEquals(120f, OriginalMotionCurves.glowSeconds(120000f), 0f)
        assertEquals(119f, OriginalMotionCurves.glowSeconds(121000f), 0f)
        assertEquals(2f, OriginalMotionCurves.glowSeconds(238000f), 0f)
        assertEquals(3f, OriginalMotionCurves.glowSeconds(239000f), 0f)
    }
    @Test fun originalMaskCoversFrameZeroThenRevealsAndRingEnds() {
        assertEquals(-0.69f, OriginalMotionCurves.circleFrame(0f).maskRadius, 0.001f)
        assertTrue(OriginalMotionCurves.circleFrame(0.2f).ringVisible)
        assertTrue(OriginalMotionCurves.circleFrame(0.8f).outerRadius >
            OriginalMotionCurves.circleFrame(0.2f).outerRadius)
        assertFalse(OriginalMotionCurves.circleFrame(1.12f).ringVisible)
        assertEquals(1f, OriginalMotionCurves.circleFrame(1.45f).maskRadius, 0.001f)
    }
    @Test fun circleRemainsPhysicalCircleInPortraitAndLandscape() {
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(100f, 0f, 500f, 1000f), 0.001f)
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(0f, 100f, 500f, 1000f), 0.001f)
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(100f, 0f, 1000f, 500f), 0.001f)
        assertEquals(0.1f, OriginalMotionCurves.circleDistance(0f, 100f, 1000f, 500f), 0.001f)
    }
}
