package com.risediary.app.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.risediary.app.MainActivity
import com.risediary.app.R
import com.risediary.app.RiseDiaryApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun post(type: ReminderType): Boolean {
        val destination = when (type) {
            ReminderType.DAILY,
            ReminderType.INACTIVE -> NotificationDestination.RECORDS
            ReminderType.MONTHLY_LENGTH -> NotificationDestination.LENGTH_HISTORY
        }
        val message = when (type) {
            ReminderType.DAILY -> context.getString(R.string.notification_reminder_daily)
            ReminderType.INACTIVE -> context.getString(R.string.notification_reminder_inactive)
            ReminderType.MONTHLY_LENGTH ->
                context.getString(R.string.notification_reminder_monthly_length)
        }
        return post(
            notificationId = type.notificationId,
            message = message,
            action = "${context.packageName}.OPEN_REMINDER.${type.storedValue}",
            destination = destination
        )
    }

    fun postTest(): Boolean = post(
        notificationId = TEST_NOTIFICATION_ID,
        message = context.getString(R.string.notification_reminder_test),
        action = "${context.packageName}.OPEN_REMINDER.TEST",
        destination = null
    )

    fun postScheduledTest(): Boolean = post(
        notificationId = TEST_NOTIFICATION_ID,
        message = context.getString(R.string.notification_reminder_scheduled_test),
        action = "${context.packageName}.OPEN_REMINDER.SCHEDULED_TEST",
        destination = null
    )

    private fun post(
        notificationId: Int,
        message: String,
        action: String,
        destination: NotificationDestination?
    ): Boolean {
        if (!notificationsAllowed()) return false
        val openIntent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            destination?.let {
                putExtra(NotificationDestination.EXTRA_DESTINATION, it.storedValue)
            }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, RiseDiaryApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TEST_NOTIFICATION_ID = 2104
    }
}
