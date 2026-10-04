package com.risediary.app.ui.video

import org.junit.Assert.*
import org.junit.Test

class VideoTransportPolicyTest {
    @Test fun unknownOrZeroDurationCannotProduceASeek() {
        assertNull(videoSeekPosition(0.5f, -9_223_372_036_854_775_807L))
        assertNull(videoSeekPosition(0.5f, 0L))
        assertEquals(0f, videoProgressFraction(20_000L, -1L), 0f)
    }

    @Test fun invalidSliderValuesNeverReachThePlayer() {
        assertNull(videoSeekPosition(Float.NaN, 60_000L))
        assertNull(videoSeekPosition(Float.POSITIVE_INFINITY, 60_000L))
        assertNull(videoSeekPosition(Float.NEGATIVE_INFINITY, 60_000L))
    }

    @Test fun seekClampsBothEndsAndKeepsMilliseconds() {
        assertEquals(0L, videoSeekPosition(-0.1f, 60_000L))
        assertEquals(60_000L, videoSeekPosition(1.1f, 60_000L))
        assertEquals(15_000L, videoSeekPosition(0.25f, 60_000L))
    }

    @Test fun longVideosDoNotOverflowAtTheRightEdge() {
        assertEquals(Long.MAX_VALUE, videoSeekPosition(1f, Long.MAX_VALUE))
        assertEquals(1f, videoProgressFraction(Long.MAX_VALUE, Long.MAX_VALUE), 0f)
    }

    @Test fun progressClampsRestoredPositionsOutsideCurrentVideo() {
        assertEquals(0f, videoProgressFraction(-1L, 60_000L), 0f)
        assertEquals(1f, videoProgressFraction(70_000L, 60_000L), 0f)
        assertEquals(0.25f, videoProgressFraction(15_000L, 60_000L), 0f)
    }

    @Test fun unknownTimeHasAPlaceholderAndLongVideoShowsHours() {
        assertEquals("--:--", formatVideoPosition(null))
        assertEquals("00:00", formatVideoPosition(-1L))
        assertEquals("02:03", formatVideoPosition(123_456L))
        assertEquals("1:02:03", formatVideoPosition(3_723_456L))
    }
}
