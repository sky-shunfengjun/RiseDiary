package com.risediary.app.ui.video

import org.junit.Assert.*
import org.junit.Test

class VideoTimerStartGateTest {
    @Test fun prepareSeekResumeAndLoopDoNotStartAnotherTimer() {
        val gate = VideoTimerStartGate()
        assertFalse(gate.onPlayback(false))
        assertTrue(gate.onPlayback(true))
        assertFalse(gate.onPlayback(true))
        gate.acknowledgeStarted()
        assertFalse(gate.onPlayback(false))
        assertFalse(gate.onPlayback(true))
    }
    @Test fun failedRequestNeedsExplicitRetry() {
        val gate = VideoTimerStartGate()
        assertTrue(gate.onPlayback(true))
        assertFalse(gate.onPlayback(false))
        assertFalse(gate.onPlayback(true))
        gate.retryAfterStartFailure()
        assertFalse(gate.onPlayback(false))
        assertTrue(gate.onPlayback(true))
    }
    @Test fun restoredStartedTimerNeverResetsItsStartTime() {
        val gate = VideoTimerStartGate()
        gate.acknowledgeStarted()
        gate.retryAfterStartFailure()
        assertFalse(gate.onPlayback(true))
    }
}
