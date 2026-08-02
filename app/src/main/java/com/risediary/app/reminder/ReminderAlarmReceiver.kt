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
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var deliveryCoordinator: ReminderDeliveryCoordinator

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        val type = ReminderType.fromStoredValue(
            intent?.getStringExtra(ReminderWorker.KEY_REMINDER_TYPE)
        ) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var deliveryCompleted = false
            try {
                deliveryCoordinator.deliver(type)
                deliveryCompleted = true
            } catch (_: Exception) {
                // The WorkManager fallback will retry delivery.
            } finally {
                try {
                    if (deliveryCompleted) {
                        try {
                            scheduler.sync(type)
                        } catch (_: Exception) {
                            // Keep the existing WorkManager fallback if rescheduling fails.
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
