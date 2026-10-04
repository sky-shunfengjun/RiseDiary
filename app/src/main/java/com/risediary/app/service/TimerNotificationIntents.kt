package com.risediary.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.risediary.app.MainActivity
import java.util.UUID

data class TimerNotificationEntry(val sessionId: String, val token: String = UUID.randomUUID().toString())

internal object TimerNotificationIntents {
    const val ACTION_OPEN = "com.risediary.app.timer.notification.OPEN"
    const val ACTION_FINISH = "com.risediary.app.timer.notification.FINISH"
    const val EXTRA_NOTIFICATION_COMMAND = "timer_notification_command"
    private const val SCHEME = "risediarytimer"

    fun activityIntent(context: Context, sessionId: String, finish: Boolean = false): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = if (finish) ACTION_FINISH else ACTION_OPEN
            data = identity(context.packageName, sessionId, if (finish) "finish" else "open")
            putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun activity(context: Context, sessionId: String, finish: Boolean = false): PendingIntent =
        PendingIntent.getActivity(context, 1001, activityIntent(context, sessionId, finish),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun serviceIntent(context: Context, sessionId: String, action: String): Intent =
        Intent(context, TimerService::class.java).apply {
            this.action = action
            data = identity(context.packageName, sessionId, action)
            putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_NOTIFICATION_COMMAND, true)
        }

    fun service(context: Context, sessionId: String, action: String): PendingIntent =
        PendingIntent.getForegroundService(context, 1001, serviceIntent(context, sessionId, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun read(intent: Intent?, packageName: String): TimerNotificationEntry? {
        val input = intent ?: return null
        val operation = when (input.action) { ACTION_OPEN -> "open"; ACTION_FINISH -> "finish"; else -> return null }
        val id = input.getStringExtra(TimerService.EXTRA_SESSION_ID) ?: return null
        if (id.isBlank() || id.length > 128 || input.data != identity(packageName, id, operation)) return null
        return TimerNotificationEntry(id)
    }

    /** Keep returning to the same page after recreation without issuing another finish. */
    fun markFinishDispatched(intent: Intent?, packageName: String) {
        val entry = read(intent, packageName) ?: return
        if (intent?.action != ACTION_FINISH) return
        intent.action = ACTION_OPEN
        intent.data = identity(packageName, entry.sessionId, "open")
    }

    fun consume(intent: Intent?) {
        if (intent?.action == ACTION_OPEN || intent?.action == ACTION_FINISH) {
            intent.action = Intent.ACTION_MAIN
            intent.data = null
            intent.removeExtra(TimerService.EXTRA_SESSION_ID)
        }
    }

    private fun identity(packageName: String, id: String, operation: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(packageName).appendPath(id).appendPath(operation).build()
}
