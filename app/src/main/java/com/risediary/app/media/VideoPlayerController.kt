package com.risediary.app.media

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

interface VideoPlayerController {
    val player: Player
    val playback: StateFlow<VideoPlaybackSnapshot?>
    val interactions: kotlinx.coroutines.flow.SharedFlow<VideoPlaybackSnapshot>
    val isPlaying: StateFlow<Boolean>
    val error: StateFlow<String?>
    fun load(snapshot: VideoPlaybackSnapshot)
    fun play()
    fun pause()
    fun seekTo(positionMillis: Long)
    fun setSpeed(speed: Float)
    fun setLoop(loop: Boolean)
    /** Covered/background pages relinquish decoder and buffer resources. */
    fun setPresentationActive(active: Boolean) {}
    fun release()
}
