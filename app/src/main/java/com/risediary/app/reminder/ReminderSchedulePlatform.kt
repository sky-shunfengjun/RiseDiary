package com.risediary.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** The Android boundary; the scheduling and recovery decisions stay in ReminderScheduler. */
internal interface ReminderSchedulePlatform {
    fun schedule(type: ReminderType, targetMillis: Long, delayMillis: Long,
        policy: ExistingWorkPolicy, plan: ReminderPlan? = null)
    fun cancel(type: ReminderType)
    fun cancelSchedule(type: ReminderType) = cancel(type)
    fun exactAlarmsAllowed(): Boolean
    fun scheduleBackgroundTest(): Boolean
    fun cancelBackgroundTest()
}

internal class AndroidReminderSchedulePlatform(private val context: Context) : ReminderSchedulePlatform {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WorkManager.getInstance(context) }

    override fun schedule(type: ReminderType, targetMillis: Long, delayMillis: Long,
        policy: ExistingWorkPolicy, plan: ReminderPlan?) {
        scheduleAlarm(type, targetMillis, plan)
        val input = Data.Builder().putString(ReminderWorker.KEY_REMINDER_TYPE, type.storedValue)
        plan?.let { input.putString(ReminderWorker.KEY_PLAN, ReminderPlanCodec.encode(it)) }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(input.build())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(type.uniqueWorkName).build()
        workManager.enqueueUniqueWork(reminderWorkName(type, plan), policy, request)
    }

    override fun cancel(type: ReminderType) {
        cancelSchedule(type)
        NotificationManagerCompat.from(context).cancel(type.notificationId)
    }

    override fun cancelSchedule(type: ReminderType) {
        alarmPendingIntent(type, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        workManager.cancelUniqueWork(type.uniqueWorkName)
        workManager.cancelAllWorkByTag(type.uniqueWorkName)
    }

    override fun exactAlarmsAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

    override fun scheduleBackgroundTest(): Boolean {
        if (!exactAlarmsAllowed()) return false
        val pendingIntent = backgroundTestPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        return try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60_000L, pendingIntent)
            true
        } catch (_: SecurityException) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            false
        }
    }

    override fun cancelBackgroundTest() {
        backgroundTestPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleAlarm(type: ReminderType, triggerAtMillis: Long, plan: ReminderPlan?) {
        val pendingIntent = alarmPendingIntent(type, PendingIntent.FLAG_UPDATE_CURRENT, plan) ?: return
        if (ReminderAlarmPolicy.precision(Build.VERSION.SDK_INT, exactAlarmsAllowed()) == ReminderAlarmPrecision.EXACT) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) { /* The access may change after the check. */ }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun alarmPendingIntent(type: ReminderType, flags: Int, plan: ReminderPlan? = null): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "${context.packageName}.REMINDER_ALARM.${type.storedValue}"
            putExtra(ReminderWorker.KEY_REMINDER_TYPE, type.storedValue)
            plan?.let { putExtra(ReminderWorker.KEY_PLAN, ReminderPlanCodec.encode(it)) }
        }
        return PendingIntent.getBroadcast(context, type.notificationId, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun backgroundTestPendingIntent(flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderTestAlarmReceiver::class.java).apply {
            action = "${context.packageName}.REMINDER_ALARM.TEST"
        }
        return PendingIntent.getBroadcast(context, 2105, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
