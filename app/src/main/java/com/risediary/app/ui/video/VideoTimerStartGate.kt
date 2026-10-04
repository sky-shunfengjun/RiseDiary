package com.risediary.app.ui.video

internal enum class VideoStartState { WAITING, REQUESTED, STARTED }

internal class VideoTimerStartGate {
    var state = VideoStartState.WAITING
        private set
    fun onPlayback(isPlaying: Boolean): Boolean {
        if (!isPlaying || state != VideoStartState.WAITING) return false
        state = VideoStartState.REQUESTED
        return true
    }
    fun acknowledgeStarted() { state = VideoStartState.STARTED }
    fun retryAfterStartFailure() {
        if (state == VideoStartState.REQUESTED) state = VideoStartState.WAITING
    }
}
