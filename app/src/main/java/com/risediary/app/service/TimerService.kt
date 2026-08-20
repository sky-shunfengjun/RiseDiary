package com.risediary.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.risediary.app.MainActivity
import com.risediary.app.R
import com.risediary.app.RiseDiaryApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject

@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var stateHolder: TimerStateHolder
    @Inject lateinit var store: TimerSessionStore
    @Inject lateinit var elapsedClock: ElapsedRealtimeClock
    @Inject lateinit var wallClock: Clock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandMutex = Mutex()
    private var tickerJob: Job? = null
    private var lastNotificationSecond = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESTORE
        if (action == ACTION_START || action == ACTION_RESUME || action == ACTION_RESTORE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(TimerSession()),
                foregroundServiceTypeForSdk(Build.VERSION.SDK_INT)
            )
        }

        serviceScope.launch {
            commandMutex.withLock {
                when (action) {
                    ACTION_START -> startNewSession()
                    ACTION_PAUSE -> pauseSession()
                    ACTION_RESUME -> resumeSession()
                    ACTION_FINISH -> finishSession()
                    ACTION_RESET -> resetSession()
                    else -> restoreSession()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startNewSession() {
        NotificationManagerCompat.from(this).cancel(MILESTONE_NOTIFICATION_ID)
        lastNotificationSecond = -1L
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            startedAtEpochMillis = wallClock.millis(),
            elapsedMillis = 0L,
            resumedAtElapsedRealtime = elapsedClock.millis(),
            resumedAtWallClock = wallClock.millis()
        )
        publish(session, persist = true)
        startTicker()
    }

    private suspend fun restoreSession() {
        val restored = store.load()
        if (!restored.isActive) {
            stateHolder.set(restored)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        lastNotificationSecond = -1L

        var normalized = if (restored.status == TimerStatus.RUNNING) {
            val elapsed = calculateElapsed(restored)
            restored.copy(
                elapsedMillis = elapsed,
                resumedAtElapsedRealtime = elapsedClock.millis(),
                resumedAtWallClock = wallClock.millis()
            )
        } else {
            restored
        }
        if (
            normalized.status == TimerStatus.RUNNING ||
            normalized.elapsedMillis >= TimerMath.MAX_DURATION_MILLIS
        ) {
            normalized = processMilestones(normalized)
            if (normalized.status == TimerStatus.LIMIT_REACHED) return
        }
        publish(normalized, persist = true)
        if (normalized.status == TimerStatus.RUNNING) startTicker()
    }

    private suspend fun pauseSession() {
        val current = stateHolder.state.value.takeIf(TimerSession::isActive)
            ?: store.load().takeIf(TimerSession::isActive)
            ?: return
        if (current.status != TimerStatus.RUNNING) return
        tickerJob?.cancel()
        val paused = processMilestones(
            current.copy(
                status = TimerStatus.PAUSED,
                elapsedMillis = calculateElapsed(current),
                resumedAtElapsedRealtime = 0L,
                resumedAtWallClock = 0L
            )
        )
        if (paused.status == TimerStatus.LIMIT_REACHED) return
        publish(paused, persist = true)
    }

    private suspend fun resumeSession() {
        val current = stateHolder.state.value.takeIf { it.status == TimerStatus.PAUSED }
            ?: store.load().takeIf { it.status == TimerStatus.PAUSED }
            ?: return
        val resumed = current.copy(
            status = TimerStatus.RUNNING,
            resumedAtElapsedRealtime = elapsedClock.millis(),
            resumedAtWallClock = wallClock.millis()
        )
        publish(resumed, persist = true)
        startTicker()
    }

    private suspend fun finishSession() {
        val current = stateHolder.state.value.takeIf(TimerSession::isActive)
            ?: store.load().takeIf(TimerSession::isActive)
            ?: return
        tickerJob?.cancel()
        val duration = if (current.status == TimerStatus.RUNNING) {
            calculateElapsed(current)
        } else {
            current.elapsedMillis
        }.coerceIn(0L, TimerMath.MAX_DURATION_MILLIS)
        val finished = current.copy(
            status = TimerStatus.FINISHED,
            elapsedMillis = duration,
            resumedAtElapsedRealtime = 0L,
            resumedAtWallClock = 0L
        )
        publish(finished, persist = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun resetSession() {
        tickerJob?.cancel()
        publish(TimerSession(), persist = true)
        NotificationManagerCompat.from(this).cancel(MILESTONE_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                val current = stateHolder.state.value
                if (current.status != TimerStatus.RUNNING) break
                val advanced = TimerMath.advance(
                    session = current,
                    elapsedRealtimeNow = elapsedClock.millis(),
                    wallClockNow = wallClock.millis()
                )
                val updated = processMilestones(advanced)
                val elapsed = updated.elapsedMillis
                if (updated.status == TimerStatus.LIMIT_REACHED) break

                val seconds = elapsed / 1_000L
                if (seconds != lastNotificationSecond) {
                    lastNotificationSecond = seconds
                    notifyState(updated)
                }
                delay(TICK_MILLIS)
            }
        }
    }

    private suspend fun processMilestones(session: TimerSession): TimerSession {
        val latest = TimerMilestones.latestUnnotified(
            elapsedMillis = session.elapsedMillis,
            notifiedMask = session.notifiedMilestonesMask
        )
        val reachedLimit = session.elapsedMillis >= TimerMath.MAX_DURATION_MILLIS
        val updated = session.copy(
            status = if (reachedLimit) TimerStatus.LIMIT_REACHED else session.status,
            elapsedMillis = if (reachedLimit) {
                TimerMath.MAX_DURATION_MILLIS
            } else {
                session.elapsedMillis
            },
            resumedAtElapsedRealtime = if (reachedLimit) 0L else session.resumedAtElapsedRealtime,
            resumedAtWallClock = if (reachedLimit) 0L else session.resumedAtWallClock,
            notifiedMilestonesMask = session.notifiedMilestonesMask or
                TimerMilestones.maskThrough(session.elapsedMillis)
        )

        stateHolder.set(updated)
        if (latest != null || reachedLimit) {
            store.save(updated)
        }

        if (reachedLimit) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyMilestone(TimerMilestone.LIMIT)
            stopSelf()
        } else if (latest != null) {
            notifyMilestone(latest)
        }
        return updated
    }

    private fun calculateElapsed(session: TimerSession): Long = TimerMath.elapsed(
        session = session,
        elapsedRealtimeNow = elapsedClock.millis(),
        wallClockNow = wallClock.millis()
    )

    private suspend fun publish(session: TimerSession, persist: Boolean) {
        stateHolder.set(session)
        if (persist) store.save(session)
        if (session.isActive) notifyState(session)
    }

    @SuppressLint("MissingPermission")
    private fun notifyState(session: TimerSession) {
        if (canPostNotifications()) {
            runCatching {
                NotificationManagerCompat.from(this)
                    .notify(NOTIFICATION_ID, buildNotification(session))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyMilestone(milestone: TimerMilestone) {
        if (!canPostNotifications()) return
        val isLimit = milestone == TimerMilestone.LIMIT
        val title = if (isLimit) {
            getString(R.string.notification_timer_limit_title)
        } else {
            getString(R.string.notification_timer_milestone_title, milestone.minutes)
        }
        val text = if (isLimit) {
            getString(R.string.notification_timer_limit_text)
        } else {
            getString(R.string.notification_timer_milestone_text)
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(
                MILESTONE_NOTIFICATION_ID,
                NotificationCompat.Builder(this, RiseDiaryApp.CHANNEL_REMINDER)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentIntent(openAppPendingIntent(MILESTONE_NOTIFICATION_ID))
                    .setAutoCancel(!isLimit)
                    .setOnlyAlertOnce(false)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            )
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(session: TimerSession): Notification {
        val title = if (session.status == TimerStatus.PAUSED) {
            getString(R.string.notification_timer_paused_title)
        } else {
            getString(R.string.notification_timer_running_title)
        }
        val text = getString(
            R.string.notification_timer_elapsed,
            formatDuration(session.elapsedMillis)
        )
        return NotificationCompat.Builder(this, RiseDiaryApp.CHANNEL_TIMER)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(openAppPendingIntent(NOTIFICATION_ID))
            .setOngoing(session.isActive)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()
    }

    private fun openAppPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RESTORE = "com.risediary.app.timer.RESTORE"
        const val ACTION_START = "com.risediary.app.timer.START"
        const val ACTION_PAUSE = "com.risediary.app.timer.PAUSE"
        const val ACTION_RESUME = "com.risediary.app.timer.RESUME"
        const val ACTION_FINISH = "com.risediary.app.timer.FINISH"
        const val ACTION_RESET = "com.risediary.app.timer.RESET"

        private const val NOTIFICATION_ID = 1001
        private const val MILESTONE_NOTIFICATION_ID = 1002
        private const val TICK_MILLIS = 200L

        private fun formatDuration(milliseconds: Long): String {
            val totalSeconds = milliseconds / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }
}

@Suppress("InlinedApi")
internal fun foregroundServiceTypeForSdk(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }
