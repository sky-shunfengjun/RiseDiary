package com.risediary.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

interface TimerController {
    val state: StateFlow<TimerSession>
    fun restore()
    fun start()
    fun pause()
    fun resume()
    fun finish()
    fun reset()
}

class ServiceTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    stateHolder: TimerStateHolder
) : TimerController {
    override val state: StateFlow<TimerSession> = stateHolder.state

    override fun restore() = send(TimerService.ACTION_RESTORE, foreground = true)
    override fun start() = send(TimerService.ACTION_START, foreground = true)
    override fun pause() = send(TimerService.ACTION_PAUSE)
    override fun resume() = send(TimerService.ACTION_RESUME, foreground = true)
    override fun finish() = send(TimerService.ACTION_FINISH)
    override fun reset() = send(TimerService.ACTION_RESET)

    private fun send(action: String, foreground: Boolean = false) {
        val intent = Intent(context, TimerService::class.java).setAction(action)
        if (foreground) ContextCompat.startForegroundService(context, intent)
        else context.startService(intent)
    }
}
