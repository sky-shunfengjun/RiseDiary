package com.risediary.app.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.risediary.app.media.VideoPlayerController
import kotlinx.coroutines.delay

internal data class VideoFullscreenState(
    val fullScreen: Boolean,
    val inSession: Boolean,
    val direction: VideoOrientation,
    val enter: () -> Unit,
    val exit: () -> Unit,
    val toggleOrientation: () -> Unit
)

/** Shared window ownership only. Player and PlayerView remain owned by their page session. */
@Composable
internal fun rememberVideoFullscreenState(controller: VideoPlayerController, active: Boolean): VideoFullscreenState {
    val activity = LocalContext.current.findVideoActivity()
    val configuration = LocalConfiguration.current
    var phase by rememberSaveable { mutableStateOf(FullscreenEntryPhase.CLOSED) }
    val inSession = phase != FullscreenEntryPhase.CLOSED
    val windowDirection = when (configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> VideoOrientation.LANDSCAPE
        Configuration.ORIENTATION_PORTRAIT -> VideoOrientation.PORTRAIT
        else -> null
    }
    var orientation by rememberSaveable(stateSaver = Saver(
        save = { listOf(it.direction.ordinal, if (it.autoDecided) 1 else 0, if (it.manual) 1 else 0) },
        restore = { VideoOrientationSession(VideoOrientation.entries[it[0]], it[1] == 1, it[2] == 1) }
    )) { mutableStateOf(VideoOrientationSession(VideoOrientation.PORTRAIT)) }
    val fullScreen = completeFullscreenEntry(phase, orientation.direction, windowDirection) == FullscreenEntryPhase.OPEN
    var originalOrientation by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var originalStatusVisible by rememberSaveable { mutableStateOf(true) }
    var originalNavVisible by rememberSaveable { mutableStateOf(true) }
    var originalBarsBehavior by rememberSaveable { mutableIntStateOf(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT) }
    val player = controller.player
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    fun requestDirection(direction: VideoOrientation) {
        val requested = if (direction == VideoOrientation.LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        activity?.let { if (it.requestedOrientation != requested) it.requestedOrientation = requested }
    }
    fun enter() {
        if (inSession || !active) return
        val window = activity?.window
        val insets = window?.decorView?.let(ViewCompat::getRootWindowInsets)
        originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        originalStatusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        originalNavVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        originalBarsBehavior = window?.let { WindowCompat.getInsetsController(it, it.decorView).systemBarsBehavior }
            ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        val size = player.videoSize
        orientation = beginVideoOrientation(size.width, size.height, size.pixelWidthHeightRatio,
            windowDirection ?: VideoOrientation.PORTRAIT)
        requestDirection(orientation.direction)
        phase = beginFullscreenEntry(orientation.direction, windowDirection)
    }
    LaunchedEffect(inSession, videoSize) {
        if (inSession) orientation = resolveVideoOrientation(orientation, videoSize.width, videoSize.height,
            videoSize.pixelWidthHeightRatio)
    }
    LaunchedEffect(phase, windowDirection, orientation.direction, active) {
        if (phase == FullscreenEntryPhase.WAITING_FOR_ROTATION && active) {
            val ready = completeFullscreenEntry(phase, orientation.direction, windowDirection)
            if (ready == FullscreenEntryPhase.OPEN) phase = ready
            else {
                delay(900L)
                phase = completeFullscreenEntry(phase, orientation.direction, windowDirection, timedOut = true)
            }
        }
    }
    LaunchedEffect(activity, inSession, active, orientation.direction) {
        if (inSession && active) requestDirection(orientation.direction)
    }
    LaunchedEffect(activity, fullScreen, active) {
        if (fullScreen && active) activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(activity, inSession, active) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val ownsWindow = inSession && active
        onDispose {
            if (ownsWindow && activity?.isChangingConfigurations != true) {
                activity?.requestedOrientation = originalOrientation
                if (originalStatusVisible) bars?.show(WindowInsetsCompat.Type.statusBars())
                else bars?.hide(WindowInsetsCompat.Type.statusBars())
                if (originalNavVisible) bars?.show(WindowInsetsCompat.Type.navigationBars())
                else bars?.hide(WindowInsetsCompat.Type.navigationBars())
                bars?.systemBarsBehavior = originalBarsBehavior
            }
        }
    }
    return VideoFullscreenState(fullScreen, inSession, orientation.direction, ::enter,
        { phase = FullscreenEntryPhase.CLOSED }, { orientation = toggleVideoOrientation(orientation) })
}

internal tailrec fun Context.findVideoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findVideoActivity()
    else -> null
}
