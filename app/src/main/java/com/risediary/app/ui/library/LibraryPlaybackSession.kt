package com.risediary.app.ui.library

import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.media.VideoPlayerController

/** Entry may load before the transition exposes it. Background return never resumes playback. */
internal class LibraryPlaybackSession(private val controller: VideoPlayerController) {
    private var selected: LocalVideoRef? = null
    private var active = false
    private var pendingAutoplay = false
    var autoplayGeneration: Long = 0L
        private set
    fun select(video: LocalVideoRef, allowAutoplay: Boolean, restored: VideoPlaybackSnapshot? = null,
        autoplayGeneration: Long = this.autoplayGeneration) {
        if (selected == video) return
        selected = video
        pendingAutoplay = allowAutoplay && autoplayGeneration == this.autoplayGeneration
        controller.load(restored ?: VideoPlaybackSnapshot(video, loop = true))
        playPending()
    }
    fun setActive(value: Boolean) {
        if (active && !value) { pendingAutoplay = false; autoplayGeneration++ }
        active = value
        if (!value) controller.pause()
        controller.setPresentationActive(value)
        playPending()
    }
    fun suspendAutoplay() { autoplayGeneration++; pendingAutoplay = false; setActive(false) }
    fun retry() {
        val video = selected ?: return
        val saved = controller.playback.value?.takeIf { it.video == video } ?: VideoPlaybackSnapshot(video, loop = true)
        controller.load(saved)
        pendingAutoplay = true
        playPending()
    }
    private fun playPending() {
        if (active && pendingAutoplay) { pendingAutoplay = false; controller.play() }
    }
}
