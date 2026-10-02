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
import kotlinx.coroutines.isActive
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
    @Inject lateinit var bootIdentity: BootIdentityProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandMutex = Mutex()
    private var tickerJob: Job? = null
    private var lastNotificationSecond = -1L
    private var lastPersistedRealtime = 0L
    private var lastPersistenceAttempt = 0L
    private var pendingTransition: TimerTransition? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESTORE
        if (action == ACTION_START || action == ACTION_RESUME || action == ACTION_RESTORE || action == ACTION_RETRY) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(TimerSession()),
                foregroundServiceTypeForSdk(Build.VERSION.SDK_INT)
            )
        }

        serviceScope.launch {
            commandMutex.withLock {
                try {
                if (pendingTransition != null && action != ACTION_RETRY) return@withLock
                when (action) {
                    ACTION_START -> startNewSession()
                    ACTION_PAUSE -> pauseSession()
                    ACTION_RESUME -> resumeSession()
                    ACTION_FINISH -> finishSession()
                    ACTION_RESET -> resetSession()
                    ACTION_RETRY -> {
                        if (pendingTransition != null) {
                            if (retryPendingTransition() && stateHolder.state.value.status == TimerStatus.RUNNING) startTicker()
                        } else restoreSession()
                    }
                    else -> restoreSession()
                }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { stateHolder.setPersistenceError(true) }
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
            resumedAtElapsedRealtime = elapsedClock.millis(),
            resumedAtWallClock = wallClock.millis(),
            bootCount = bootIdentity.currentBootCount()
        )
        if (commitTransition(prepareTimerTransition(session), persist = true)) startTicker()
    }

    private suspend fun restoreSession() {
        // load() normalizes the boot identity before any stored state can reach the UI.
        val current = stateHolder.state.value
        val restored = if (current.status == TimerStatus.IDLE) store.load()
            else if (current.status == TimerStatus.RUNNING)
                TimerMath.advance(current, elapsedClock.millis(), wallClock.millis()) else current
        lastNotificationSecond = -1L
        val transition = prepareTimerTransition(restored)
        if (commitTransition(transition, persist = true) && transition.session.status == TimerStatus.RUNNING) startTicker()
    }

    private suspend fun pauseSession() {
        val current = currentActiveSession() ?: return
        if (current.status != TimerStatus.RUNNING) return
        tickerJob?.cancel()
        val paused = current.copy(
            status = TimerStatus.PAUSED,
            elapsedMillis = calculateElapsed(current),
            resumedAtElapsedRealtime = 0L,
            resumedAtWallClock = 0L,
            bootCount = bootIdentity.currentBootCount()
        )
        // Includes the pause-at-limit path: it must persist before stopping too.
        commitTransition(prepareTimerTransition(paused), persist = true)
    }

    private suspend fun resumeSession() {
        val current = stateHolder.state.value.takeIf { it.status == TimerStatus.PAUSED }
            ?: store.load().takeIf { it.status == TimerStatus.PAUSED }
            ?: return
        val resumed = current.copy(
            status = TimerStatus.RUNNING,
            resumedAtElapsedRealtime = elapsedClock.millis(),
            resumedAtWallClock = wallClock.millis(),
            bootCount = bootIdentity.currentBootCount()
        )
        if (commitTransition(prepareTimerTransition(resumed), persist = true) &&
            stateHolder.state.value.status == TimerStatus.RUNNING) startTicker()
    }

    private suspend fun finishSession() {
        val current = currentActiveSession() ?: return
        tickerJob?.cancel()
        val finished = current.copy(
            status = TimerStatus.FINISHED,
            elapsedMillis = calculateElapsed(current),
            resumedAtElapsedRealtime = 0L,
            resumedAtWallClock = 0L,
            bootCount = bootIdentity.currentBootCount()
        )
        commitTransition(prepareTimerTransition(finished), persist = true)
    }

    private suspend fun resetSession() {
        tickerJob?.cancel()
        if (commitTransition(prepareTimerTransition(TimerSession()), persist = true)) {
            NotificationManagerCompat.from(this).cancel(MILESTONE_NOTIFICATION_ID)
        }
    }

    private suspend fun currentActiveSession(): TimerSession? =
        stateHolder.state.value.takeIf(TimerSession::isActive) ?: store.load().takeIf(TimerSession::isActive)

    /** Every tick and checkpoint shares the command lock, so no old RUNNING save follows a terminal one. */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                var keepTicking = true
                commandMutex.withLock {
                    if (pendingTransition != null) {
                        if (elapsedClock.millis() - lastPersistenceAttempt >= TIMER_CHECKPOINT_MILLIS) {
                            retryPendingTransition()
                        }
                        keepTicking = pendingTransition != null || stateHolder.state.value.status == TimerStatus.RUNNING
                        return@withLock
                    }
                    val current = stateHolder.state.value
                    if (current.status != TimerStatus.RUNNING) {
                        keepTicking = false
                        return@withLock
                    }
                    val now = elapsedClock.millis()
                    val advanced = TimerMath.advance(current, now, wallClock.millis())
                    val transition = prepareTimerTransition(advanced)
                    val persist = shouldPersistTimerTick(current, transition.session, now, lastPersistedRealtime)
                    val committed = commitTransition(transition, persist)
                    if (committed && transition.session.status == TimerStatus.LIMIT_REACHED) {
                        keepTicking = false
                    }
                }
                if (!keepTicking) break
                delay(TICK_MILLIS)
            }
        }
    }

    private suspend fun retryPendingTransition(): Boolean {
        val pending = pendingTransition ?: return true
        val transition = if (pending.session.status == TimerStatus.RUNNING) {
            prepareTimerTransition(TimerMath.advance(pending.session, elapsedClock.millis(), wallClock.millis()))
                .let { if (it.milestone == null) it.copy(milestone = pending.milestone) else it }
        } else pending
        return commitTransition(transition, persist = true)
    }

    private suspend fun commitTransition(transition: TimerTransition, persist: Boolean): Boolean {
        if (persist) lastPersistenceAttempt = elapsedClock.millis()
        return try {
            TimerSessionCommitter(
                save = { store.save(it); lastPersistedRealtime = elapsedClock.millis() },
                publish = { stateHolder.set(it) },
                notify = ::notifyMilestone,
                stop = { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            ).commit(transition, persist)
            pendingTransition = null
            stateHolder.setPersistenceError(false)
            val seconds = transition.session.elapsedMillis / 1_000L
            if (transition.session.isActive && seconds != lastNotificationSecond) {
                lastNotificationSecond = seconds
                notifyState(transition.session)
            }
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the durable transition and the service alive; explicit retry or a checkpoint can retry it.
            pendingTransition = transition
            stateHolder.setPersistenceError(true)
            false
        }
    }

    private fun calculateElapsed(session: TimerSession): Long =
        TimerMath.elapsedInCurrentProcess(session, elapsedClock.millis())
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
        const val ACTION_RETRY = "com.risediary.app.timer.RETRY_PERSISTENCE"
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
