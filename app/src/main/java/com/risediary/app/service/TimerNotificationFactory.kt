package com.risediary.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.risediary.app.R
import com.risediary.app.RiseDiaryApp
import com.risediary.app.util.formatTimerClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject

internal object TimerLiveUpdateSupport {
    fun capabilities(context: Context): TimerNotificationCapabilities {
        val manager = context.getSystemService(NotificationManager::class.java)
        val supported = TimerNotificationPolicy.supportsLiveUpdates(Build.VERSION.SDK_INT)
        val importance = manager?.getNotificationChannel(RiseDiaryApp.CHANNEL_TIMER)?.importance
            ?: NotificationManager.IMPORTANCE_LOW
        val allowed = manager?.areNotificationsEnabled() == true &&
            importance != NotificationManager.IMPORTANCE_NONE &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        return TimerNotificationCapabilities(Build.VERSION.SDK_INT, allowed,
            supported && Build.VERSION.SDK_INT >= 36 && runCatching { manager?.canPostPromotedNotifications() == true }.getOrDefault(false),
            importance)
    }

    fun openSettings(context: Context, promotion: Boolean) {
        val intent = if (promotion && Build.VERSION.SDK_INT >= 36)
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) }
        catch (_: Exception) {
            runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

class TimerNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val elapsedClock: ElapsedRealtimeClock,
    private val wallClock: Clock
) {
    @SuppressLint("InlinedApi")
    internal fun build(session: TimerSession, enabled: Boolean, commandFailed: Boolean = false,
                       dismissed: Boolean = session.liveUpdateDismissed,
                       caps: TimerNotificationCapabilities = TimerLiveUpdateSupport.capabilities(context)): Notification {
        val now = elapsedClock.millis()
        val requestPromotion = TimerNotificationPolicy.requestPromotion(session, enabled, caps, dismissed, commandFailed)
        val waiting = session.finishCandidate != null
        val running = session.status == TimerStatus.RUNNING && !waiting
        val elapsed = TimerNotificationPolicy.elapsed(session, now)
        val title = when {
            waiting -> R.string.notification_timer_confirm_title
            session.status == TimerStatus.PAUSED -> R.string.notification_timer_paused_title
            session.isTerminal -> R.string.notification_timer_finished_title
            session.status == TimerStatus.IDLE -> R.string.notification_timer_preparing_title
            else -> R.string.notification_timer_running_title
        }
        val builder = Notification.Builder(context, RiseDiaryApp.CHANNEL_TIMER)
            .setContentTitle(context.getString(title))
            .setContentText(if (running) context.getString(R.string.notification_timer_return)
                else context.getString(R.string.notification_timer_elapsed, formatTimerClock(elapsed)))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(session.isActive)
            .setOnlyAlertOnce(true)
            .setColorized(false)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(TimerNotificationPolicy.chronometerWhen(session, now, wallClock.millis()))
            .setShowWhen(running)
            .setUsesChronometer(running)
            .setChronometerCountDown(false)
            .addExtras(Bundle().apply {
                putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, requestPromotion)
            })
        val id = session.sessionId
        if (id != null) {
            builder.setContentIntent(TimerNotificationIntents.activity(context, id))
            builder.setDeleteIntent(TimerNotificationIntents.service(context, id, TimerService.ACTION_NOTIFICATION_DISMISSED))
            TimerNotificationPolicy.actions(session, commandFailed).forEach { action ->
                val (label, icon, pendingIntent) = when (action) {
                    TimerNotificationAction.PAUSE -> Triple(R.string.notification_timer_pause,
                        android.R.drawable.ic_media_pause,
                        TimerNotificationIntents.service(context, id, TimerService.ACTION_PAUSE))
                    TimerNotificationAction.RESUME -> Triple(R.string.notification_timer_resume,
                        android.R.drawable.ic_media_play,
                        TimerNotificationIntents.service(context, id, TimerService.ACTION_RESUME))
                    TimerNotificationAction.FINISH -> Triple(R.string.notification_timer_finish,
                        android.R.drawable.ic_menu_close_clear_cancel,
                        TimerNotificationIntents.activity(context, id, finish = true))
                }
                builder.addAction(Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, icon),
                    context.getString(label), pendingIntent)
                    .setAuthenticationRequired(action == TimerNotificationAction.FINISH).build())
            }
        }
        val notification = builder.build()
        // Android 16's original native promotion rules require colorization; newer
        // rules require the opt-in extra and reject colorization. Probe the public
        // format check so OEM backports work without guessing a minor version.
        if (requestPromotion && Build.VERSION.SDK_INT >= 36 &&
            runCatching { notification.hasPromotableCharacteristics() }.getOrNull() == false) {
            return builder.setColorized(true).build()
        }
        return notification
    }
}
