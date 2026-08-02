package com.risediary.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderTestAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            notifier.postScheduledTest()
        } finally {
            scheduler.cancelBackgroundTest()
        }
    }
}
