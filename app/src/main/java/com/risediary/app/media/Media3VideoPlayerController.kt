@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.risediary.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Owned by one page ViewModel; it has no timer or foreground service. */
class Media3VideoPlayerController(context: Context) : VideoPlayerController {
    private val engine = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var released = false
    private var video: LocalVideoRef? = null
    private val snapshot = MutableStateFlow<VideoPlaybackSnapshot?>(null)
    private val playing = MutableStateFlow(false)
    private val failure = MutableStateFlow<String?>(null)
    private val actions = kotlinx.coroutines.flow.MutableSharedFlow<VideoPlaybackSnapshot>(extraBufferCapacity = 8)
    override val interactions = actions.asSharedFlow()
    override val playback = snapshot.asStateFlow()
    override val isPlaying = playing.asStateFlow()
    override val error = failure.asStateFlow()

    // PlayerView's native transport/seek controls also go through this controller.
    override val player: Player = object : ForwardingPlayer(engine) {
        override fun play() = this@Media3VideoPlayerController.play()
        override fun pause() = this@Media3VideoPlayerController.pause()
        override fun setPlayWhenReady(value: Boolean) {
            if (value) play() else pause()
        }
        override fun seekTo(positionMs: Long) = this@Media3VideoPlayerController.seekTo(positionMs)
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) = seekTo(positionMs)
        override fun setPlaybackParameters(parameters: PlaybackParameters) =
            setSpeed(parameters.speed)
        override fun setRepeatMode(repeatMode: Int) = setLoop(repeatMode == Player.REPEAT_MODE_ONE)
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing.value = isPlaying
            publish()
        }
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onPlayerError(error: PlaybackException) {
            engine.pause()
            failure.value = "视频无法播放，请重试或更换文件"
        }
        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            // Focus restoration must not restart private media without an explicit tap.
            if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) pause()
        }
    }

    init {
        engine.addListener(listener)
        scope.launch {
            while (true) {
                delay(500)
                if (engine.isPlaying) publish()
            }
        }
    }

    override fun load(snapshot: VideoPlaybackSnapshot) {
        check(!released)
        require(validateLocalVideoFields(snapshot.video.uriString, snapshot.video.displayName,
            snapshot.video.mimeType) == null)
        engine.pause()
        video = snapshot.video
        failure.value = null
        engine.setMediaItem(MediaItem.fromUri(Uri.parse(snapshot.video.uriString)))
        engine.seekTo(snapshot.positionMillis.coerceAtLeast(0L))
        setSpeed(snapshot.speed)
        setLoop(snapshot.loop)
        engine.playWhenReady = false
        engine.prepare()
        publish()
    }

    override fun play() {
        if (!released && video != null && failure.value == null) engine.play()
    }
    override fun pause() {
        if (!released) {
            engine.pause()
            publish()
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    override fun seekTo(positionMillis: Long) {
        if (!released) {
            engine.seekTo(positionMillis.coerceAtLeast(0L))
            publish()
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    override fun setSpeed(speed: Float) {
        if (!released) {
            engine.setPlaybackSpeed(speed.takeIf { it in SPEEDS } ?: 1f)
            publish()
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    override fun setLoop(loop: Boolean) {
        if (!released) {
            engine.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            publish()
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    private fun publish() {
        if (!released) video?.let {
            snapshot.value = VideoPlaybackSnapshot(it, engine.currentPosition.coerceAtLeast(0L),
                engine.playbackParameters.speed, engine.repeatMode == Player.REPEAT_MODE_ONE)
        }
    }
    override fun release() {
        if (released) return
        publish()
        released = true
        scope.cancel()
        engine.removeListener(listener)
        engine.clearVideoSurface()
        engine.release()
        playing.value = false
    }

    companion object { val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f) }
}
