package com.risediary.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.risediary.app.media.VideoPlaybackSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface TimerController {
    val state: StateFlow<TimerSession>
    fun restore()
    fun start(request: TimerStartRequest)
    fun pause(sessionId: String)
    fun resume(sessionId: String)
    fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate? = null)
    fun confirmFinish(sessionId: String)
    fun cancelFinish(sessionId: String)
    fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean = false)
    fun retryPersistence() = restore()
    fun reset(sessionId: String)
    fun discard(sessionId: String)
}

class ServiceTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: TimerStateHolder
) : TimerController {
    override val state = stateHolder.state
    override fun restore() = send(TimerService.ACTION_RESTORE, foreground = true)
    override fun start(request: TimerStartRequest) = send(TimerService.ACTION_START, request.sessionId, true) {
        putExtra(TimerService.EXTRA_START, json.encodeToString(request))
    }
    override fun pause(sessionId: String) = send(TimerService.ACTION_PAUSE, sessionId)
    override fun resume(sessionId: String) = send(TimerService.ACTION_RESUME, sessionId, true)
    override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) {
        val current = state.value.takeIf { it.sessionId == sessionId && it.isActive }
        val captured = candidate ?: current?.let { TimerFinishPolicy.capture(it, wallClockNow, elapsedRealtimeNow) }
        send(TimerService.ACTION_REQUEST_FINISH, sessionId) {
            putExtra(TimerService.EXTRA_WALL, wallClockNow)
            putExtra(TimerService.EXTRA_MONO, elapsedRealtimeNow)
            captured?.let { putExtra(TimerService.EXTRA_CANDIDATE, json.encodeToString(it)) }
        }
    }
    override fun confirmFinish(sessionId: String) = send(TimerService.ACTION_CONFIRM_FINISH, sessionId)
    override fun cancelFinish(sessionId: String) = send(TimerService.ACTION_CANCEL_FINISH, sessionId)
    override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) =
        send(TimerService.ACTION_PLAYBACK, sessionId) {
            putExtra(TimerService.EXTRA_VIDEO, json.encodeToString(snapshot))
            putExtra(TimerService.EXTRA_IMMEDIATE, immediate)
        }
    override fun retryPersistence() = send(TimerService.ACTION_RETRY, foreground = true)
    override fun reset(sessionId: String) = send(TimerService.ACTION_RESET, sessionId)
    override fun discard(sessionId: String) = send(TimerService.ACTION_DISCARD, sessionId)

    private fun send(action: String, sessionId: String? = null, foreground: Boolean = false,
                     configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, TimerService::class.java).setAction(action).apply {
            putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
            configure()
        }
        // A fresh user action must await its own outcome, not a previous failed command's message.
        if (action != TimerService.ACTION_PLAYBACK) stateHolder.setCommandError(null)
        try {
            if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        } catch (_: Exception) {
            stateHolder.setCommandError("无法执行计时操作，请重试")
        }
    }
    private companion object { val json = Json { encodeDefaults = true } }
}
