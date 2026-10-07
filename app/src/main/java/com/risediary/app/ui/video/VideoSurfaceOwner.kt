@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.ui.video

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.ui.PlayerView
import com.risediary.app.R
import com.risediary.app.media.VideoPlayerController

/** Page-owned native video view. Only its small AndroidView hosts are replaced. */
internal class VideoSurfaceOwner(private val controller: VideoPlayerController) {
    private var view: PlayerView? = null
    private var released = false

    fun mount(host: FrameLayout): PlayerView {
        check(!released)
        // Called from AndroidView.factory/update on the UI thread; construction stays in factory.
        val surface = view ?: (LayoutInflater.from(host.context).inflate(
            R.layout.local_video_player_view, host, false
        ) as PlayerView).also { view = it; it.useController = false; it.player = controller.player }
        if (surface.parent !== host) {
            (surface.parent as? ViewGroup)?.removeView(surface)
            host.addView(surface, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        return surface
    }

    fun unmount(host: FrameLayout) {
        // A previous host can be released after the next one already owns the surface.
        view?.takeIf { it.parent === host }?.let(host::removeView)
    }

    fun release() {
        if (released) return
        released = true
        view?.let {
            it.setOnClickListener(null)
            it.player = null
            (it.parent as? ViewGroup)?.removeView(it)
        }
        view = null
    }
}
