package com.risediary.app.ui.video

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.risediary.app.media.VideoPlayerController

internal class VideoPlaybackLifecycleObserver(private val controller: VideoPlayerController) : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) controller.pause()
    }
}
