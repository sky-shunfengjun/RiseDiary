package com.risediary.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isSupportedTimeChangeAction(intent?.action)) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.syncAll()
            } catch (_: Exception) {
                // WorkManager keeps the existing requests if rescheduling temporarily fails.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun isSupportedTimeChangeAction(action: String?): Boolean = action in setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
)

private const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
