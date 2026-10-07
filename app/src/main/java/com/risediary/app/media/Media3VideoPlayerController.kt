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
class Media3VideoPlayerController(context: Context,
    private val coordinator: VideoResourceCoordinator = VideoResourceCoordinator(),
    private val diagnostics: VideoDiagnostics = VideoDiagnostics(),
    initiallyActive: Boolean = true) : VideoPlayerController {
    private val engine = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val diagnosticToken = Any()
    private var polling: kotlinx.coroutines.Job? = null
    private var released = false
    private var presentationActive = initiallyActive
    private var prepared = false
    private var mutating = false
    private var firstFramePlayed = false
    private var wasBuffering = false
    private var decoderName: String? = null
    private var newDiagnosticSession = true
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
            if (isPlaying) firstFramePlayed = true
        }
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onPlayerError(error: PlaybackException) {
            engine.pause()
            failure.value = "视频无法播放，请重试或更换文件（错误 ${error.errorCode}）"
        }
        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            // Focus restoration must not restart private media without an explicit tap.
            if (reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) pause()
        }
    }

    private val analytics = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            this@Media3VideoPlayerController.decoderName = decoderName
            diagnostics.update(diagnosticToken) { it.copy(decoder = decoderName) }
        }
        override fun onVideoInputFormatChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
            diagnostics.update(diagnosticToken) {
                it.copy(width = format.width, height = format.height, frameRate = format.frameRate)
            }
        }
        override fun onDroppedVideoFrames(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            droppedFrames: Int, elapsedMs: Long) {
            diagnostics.update(diagnosticToken) { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
        }
        override fun onPlaybackStateChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, state: Int) {
            val buffering = state == Player.STATE_BUFFERING
            diagnostics.update(diagnosticToken) {
                it.copy(buffering = when (state) {
                    Player.STATE_BUFFERING -> if (firstFramePlayed) "播放中等待" else "首次加载"
                    Player.STATE_READY -> if (engine.isPlaying) "正在播放" else "可播放"
                    Player.STATE_ENDED -> "播放结束"
                    else -> "未准备"
                }, bufferEvents = it.bufferEvents + if (buffering && !wasBuffering && firstFramePlayed) 1 else 0)
            }
            wasBuffering = buffering
        }
        override fun onPlayerError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, error: PlaybackException) {
            diagnostics.update(diagnosticToken) { it.copy(errorCode = error.errorCode) }
        }
    }

    init {
        engine.addListener(listener)
        engine.addAnalyticsListener(analytics)
    }

    private fun startPolling() {
        polling?.cancel()
        polling = scope.launch {
            while (true) {
                delay(500)
                if (engine.isPlaying) publish()
                if (prepared && diagnostics.enabled.value) diagnostics.update(diagnosticToken) {
                    val size = engine.videoSize
                    it.copy(bufferedMillis = engine.totalBufferedDuration, decoder = decoderName,
                        frameRate = engine.videoFormat?.frameRate ?: it.frameRate,
                        width = if (size.width > 0) size.width else it.width,
                        height = if (size.height > 0) size.height else it.height,
                        buffering = if (engine.isPlaying) "正在播放" else it.buffering)
                }
            }
        }
    }

    override fun load(snapshot: VideoPlaybackSnapshot) {
        check(!released)
        require(validateLocalVideoFields(snapshot.video.uriString, snapshot.video.displayName, snapshot.video.mimeType) == null)
        relinquish()
        video = snapshot.video
        this.snapshot.value = snapshot.copy(positionMillis = snapshot.positionMillis.coerceAtLeast(0L),
            speed = snapshot.speed.takeIf { it in SPEEDS } ?: 1f)
        failure.value = null
        firstFramePlayed = false; wasBuffering = false; decoderName = null; newDiagnosticSession = true
        if (presentationActive) prepare()
    }

    private fun prepare() {
        val saved = snapshot.value ?: return
        if (released || prepared || !presentationActive) return
        coordinator.acquire(this, ::relinquish)
        mutating = true
        try {
            engine.playWhenReady = false
            engine.setMediaItem(MediaItem.fromUri(Uri.parse(saved.video.uriString)), saved.positionMillis)
            engine.setPlaybackSpeed(saved.speed)
            engine.repeatMode = if (saved.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            diagnostics.attach(diagnosticToken, newDiagnosticSession)
            newDiagnosticSession = false
            prepared = true
            engine.prepare()
            startPolling()
        } catch (error: Exception) {
            relinquish()
            throw error
        } finally { mutating = false }
        publish()
    }

    /** stop releases decoder and buffered media; player/surface identity remain stable. */
    private fun relinquish() {
        if (released) return
        publish()
        val saved = snapshot.value
        val changed = engine.playWhenReady || engine.isPlaying
        prepared = false
        polling?.cancel(); polling = null
        mutating = true
        try { engine.pause(); engine.stop() }
        finally { mutating = false; playing.value = false; snapshot.value = saved }
        coordinator.release(this)
        diagnostics.release(diagnosticToken)
        if (changed) saved?.let { actions.tryEmit(it) }
    }

    override fun setPresentationActive(active: Boolean) {
        if (released) return
        presentationActive = active
        if (active) prepare() else if (prepared) relinquish()
    }
    override fun play() {
        if (!released && presentationActive && video != null && failure.value == null) { prepare(); engine.play() }
    }
    override fun pause() {
        if (!released) {
            val changed = engine.playWhenReady || engine.isPlaying
            engine.pause(); publish()
            if (changed) snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    override fun seekTo(positionMillis: Long) {
        if (!released) {
            snapshot.value = snapshot.value?.copy(positionMillis = positionMillis.coerceAtLeast(0L))
            if (prepared) { engine.seekTo(positionMillis.coerceAtLeast(0L)); publish() }
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    override fun setSpeed(speed: Float) {
        if (!released) {
            val valid = speed.takeIf { it in SPEEDS } ?: 1f
            snapshot.value = snapshot.value?.copy(speed = valid)
            if (prepared) { engine.setPlaybackSpeed(valid); publish() }
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    override fun setLoop(loop: Boolean) {
        if (!released) {
            snapshot.value = snapshot.value?.copy(loop = loop)
            if (prepared) { engine.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; publish() }
            snapshot.value?.let { actions.tryEmit(it) }
        }
    }
    private fun publish() {
        if (!released && prepared && !mutating) video?.let {
            snapshot.value = VideoPlaybackSnapshot(it, engine.currentPosition.coerceAtLeast(0L),
                engine.playbackParameters.speed, engine.repeatMode == Player.REPEAT_MODE_ONE)
        }
    }
    override fun release() {
        if (released) return
        relinquish()
        released = true
        scope.cancel()
        engine.removeListener(listener)
        engine.removeAnalyticsListener(analytics)
        engine.clearVideoSurface()
        engine.release()
        playing.value = false
    }

    companion object { val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f) }
}
