package com.risediary.app.ui.video

import org.junit.Assert.*
import org.junit.Test

class FullscreenVideoPolicyTest {
    @Test fun displaySizeChoosesDirectionIncludingPixelRatio() {
        assertEquals(VideoOrientation.LANDSCAPE, videoOrientation(1920, 1080, 1f))
        assertEquals(VideoOrientation.PORTRAIT, videoOrientation(1080, 1920, 1f))
        assertEquals(VideoOrientation.LANDSCAPE, videoOrientation(600, 800, 2f))
        assertEquals(VideoOrientation.PORTRAIT, videoOrientation(1200, 800, 0.5f))
    }
    @Test fun unknownOrSquareKeepsCurrentDirection() {
        for (size in listOf(0 to 0, 0 to 720, 1000 to 1000)) {
            val state = beginVideoOrientation(size.first, size.second, 1f, VideoOrientation.PORTRAIT)
            assertEquals(VideoOrientation.PORTRAIT, state.direction)
            assertFalse(state.autoDecided)
        }
        assertNull(videoOrientation(100, 100, Float.NaN))
        assertNull(videoOrientation(100, 100, Float.POSITIVE_INFINITY))
        assertNull(videoOrientation(100, 100, 0f))
    }
    @Test fun delayedSizeIsResolvedOnce() {
        val start = beginVideoOrientation(0, 0, 1f, VideoOrientation.PORTRAIT)
        val resolved = resolveVideoOrientation(start, 1920, 1080, 1f)
        assertEquals(VideoOrientation.LANDSCAPE, resolved.direction)
        assertTrue(resolved.autoDecided)
        assertEquals(resolved, resolveVideoOrientation(resolved, 1080, 1920, 1f))
    }
    @Test fun manualChoiceBlocksDelayedAutomaticChange() {
        val start = beginVideoOrientation(0, 0, 1f, VideoOrientation.PORTRAIT)
        val manual = toggleVideoOrientation(start)
        assertTrue(manual.manual)
        assertEquals(VideoOrientation.LANDSCAPE, manual.direction)
        assertEquals(manual, resolveVideoOrientation(manual, 1080, 1920, 1f))
    }
    @Test fun eachNewEntranceDropsPreviousManualOverride() {
        val previous = toggleVideoOrientation(beginVideoOrientation(1920, 1080, 1f, VideoOrientation.PORTRAIT))
        assertEquals(VideoOrientation.PORTRAIT, previous.direction)
        val next = beginVideoOrientation(1920, 1080, 1f, previous.direction)
        assertEquals(VideoOrientation.LANDSCAPE, next.direction)
        assertFalse(next.manual)
    }
}
