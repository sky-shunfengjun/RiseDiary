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
import com.risediary.app.util.formatTimerClock
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
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.media.VideoGrantRegistry
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var stateHolder: TimerStateHolder
    @Inject lateinit var store: TimerSessionStore
    @Inject lateinit var elapsedClock: ElapsedRealtimeClock
    @Inject lateinit var wallClock: Clock
    @Inject lateinit var bootIdentity: BootIdentityProvider
    @Inject lateinit var maintenanceGate: DataMaintenanceGate
    @Inject lateinit var videoGrants: VideoGrantRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { encodeDefaults = true }
    private val commandMutex = Mutex()
    private var tickerJob: Job? = null
    private var lastNotificationSecond = -1L
    private var lastPersistedRealtime = 0L
    private var lastPersistenceAttempt = 0L
    private var pendingTransition: TimerTransition? = null
    private var handledStartId = 0
    private var lastStopRequestId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESTORE
        if (action == ACTION_START || action == ACTION_RESUME || action == ACTION_RESTORE || action == ACTION_RETRY) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(stateHolder.state.value),
                foregroundServiceTypeForSdk(Build.VERSION.SDK_INT)
            )
        }

        serviceScope.launch {
            commandMutex.withLock {
                // Only the command that owns the lock may stop its service start request.
                handledStartId = startId
                try {
                    if (pendingTransition != null && action !in setOf(ACTION_RETRY, ACTION_REQUEST_FINISH, ACTION_DISCARD)) return@withLock
                    when (action) {
                        ACTION_START -> startNewSession(json.decodeFromString<TimerStartRequest>(
                            requireNotNull(intent?.getStringExtra(EXTRA_START))))
                        ACTION_PAUSE -> pauseSession(intent?.getStringExtra(EXTRA_SESSION_ID))
                        ACTION_RESUME -> resumeSession(intent?.getStringExtra(EXTRA_SESSION_ID))
                        ACTION_REQUEST_FINISH -> requestFinish(intent)
                        ACTION_CONFIRM_FINISH -> confirmFinish(intent?.getStringExtra(EXTRA_SESSION_ID))
                        ACTION_CANCEL_FINISH -> cancelFinish(intent?.getStringExtra(EXTRA_SESSION_ID))
                        ACTION_PLAYBACK -> updatePlayback(intent)
                        ACTION_RESET -> resetSession(intent?.getStringExtra(EXTRA_SESSION_ID))
                        ACTION_DISCARD -> discardSession(intent?.getStringExtra(EXTRA_SESSION_ID))
                        ACTION_RETRY -> {
                            if (pendingTransition != null) {
                                if (retryPendingTransition() && stateHolder.state.value.status == TimerStatus.RUNNING) startTicker()
                            } else restoreSession()
                        }
                        else -> restoreSession()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    stateHolder.setCommandError("无法执行操作，请先处理当前计时后重试")
                } finally {
                    if (!stateHolder.state.value.isActive && pendingTransition == null) stopCompletedCommand(startId)
                }
            }
        }
        return START_STICKY
    }

    private suspend fun effectiveSession(): TimerSession = try {
        stateHolder.restoreIfIdle(store::load)
    } catch (failure: Exception) {
        stateHolder.setPersistenceError(true)
        throw failure
    }

    private suspend fun startNewSession(request: TimerStartRequest) {
        maintenanceGate.write {
            val current = effectiveSession()
            val session = TimerSessionPolicy.start(current, request,
                wallClock.millis(), elapsedClock.millis(), bootIdentity.currentBootCount())
            if (session == current) return@write
            NotificationManagerCompat.from(this).cancel(MILESTONE_NOTIFICATION_ID)
            lastNotificationSecond = -1L
            stateHolder.setCommandError(null)
            videoGrants.retain("timer-service", listOfNotNull(session.video?.video?.uriString).toSet())
            if (commitTransition(prepareTimerTransition(session), true)) startTicker()
        }
    }

    private suspend fun restoreSession() {
        val current = effectiveSession()
        val restored = if (current.status == TimerStatus.RUNNING)
            TimerMath.advance(current, elapsedClock.millis(), wallClock.millis()) else current
        lastNotificationSecond = -1L
        val transition = prepareTimerTransition(restored)
        if (commitTransition(transition, true) && transition.session.status == TimerStatus.RUNNING) startTicker()
    }

    private suspend fun matchingSession(id: String?): TimerSession? =
        effectiveSession().takeIf { TimerSessionPolicy.matches(it, id) }

    private suspend fun pauseSession(id: String?) {
        val current = matchingSession(id) ?: return
        if (current.status != TimerStatus.RUNNING || current.finishCandidate != null) return
        tickerJob?.cancel()
        val advanced = TimerMath.advance(current, elapsedClock.millis(), wallClock.millis())
        val paused = advanced.copy(status = TimerStatus.PAUSED,
            resumedAtElapsedRealtime = 0L, resumedAtWallClock = 0L,
            bootCount = bootIdentity.currentBootCount())
        commitTransition(prepareTimerTransition(paused), true)
    }

    private suspend fun resumeSession(id: String?) {
        val current = matchingSession(id) ?: return
        if (current.status != TimerStatus.PAUSED || current.finishCandidate != null) return
        val resumed = current.copy(status = TimerStatus.RUNNING,
            resumedAtElapsedRealtime = elapsedClock.millis(), resumedAtWallClock = wallClock.millis(),
            bootCount = bootIdentity.currentBootCount())
        if (commitTransition(prepareTimerTransition(resumed), true) &&
            stateHolder.state.value.status == TimerStatus.RUNNING) startTicker()
    }

    private suspend fun requestFinish(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_SESSION_ID)
        // A failed video checkpoint must not swallow the finish pressed immediately after it.
        val current = (pendingTransition?.session ?: effectiveSession())
            .takeIf { TimerSessionPolicy.matches(it, id) } ?: return
        if (current.finishCandidate != null || current.status == TimerStatus.FINISHED || current.status == TimerStatus.IDLE) return
        val wall = intent?.getLongExtra(EXTRA_WALL, 0L) ?: return
        val mono = intent.getLongExtra(EXTRA_MONO, -1L)
        if (wall <= 0L || mono < 0L) return
        val candidate = intent.getStringExtra(EXTRA_CANDIDATE)?.let {
            json.decodeFromString<TimerFinishCandidate>(it)
        } ?: run {
            // A cold action computes at the captured uptime, never at the end of load().
            val captured = store.loadAt(mono, wall)
            if (!captured.isActive) return
            TimerFinishPolicy.capture(captured, wall, mono)
        }
        commitTransition(prepareTimerTransition(TimerFinishPolicy.attach(current, candidate)), true)
    }

    private suspend fun confirmFinish(id: String?) {
        val current = matchingSession(id) ?: return
        if (current.isTerminal && current.finishCandidate == null) return
        if (current.finishCandidate == null) return
        tickerJob?.cancel()
        commitTransition(prepareTimerTransition(TimerFinishPolicy.confirm(current)), true)
    }

    private suspend fun cancelFinish(id: String?) {
        val current = matchingSession(id) ?: return
        if (current.finishCandidate == null) return
        val cancelled = TimerFinishPolicy.cancel(current, wallClock.millis(), elapsedClock.millis())
        if (commitTransition(prepareTimerTransition(cancelled), true) &&
            cancelled.status == TimerStatus.RUNNING) startTicker()
    }

    private suspend fun discardSession(id: String?) {
        val current = matchingSession(id) ?: return
        val discarded = TimerSessionPolicy.discard(current, id)
        if (discarded === current) return
        tickerJob?.cancel()
        // Discard bypasses FINISHED so no form can be created from this user action.
        if (commitTransition(prepareTimerTransition(discarded), true)) {
            NotificationManagerCompat.from(this).cancel(MILESTONE_NOTIFICATION_ID)
            videoGrants.requestCleanup()
        }
    }

    private suspend fun resetSession(id: String?) {
        val current = matchingSession(id) ?: return
        // Running timers can only finish through the captured-candidate path.
        if (!current.isTerminal) return
        tickerJob?.cancel()
        if (commitTransition(prepareTimerTransition(TimerSession()), true)) {
            NotificationManagerCompat.from(this).cancel(MILESTONE_NOTIFICATION_ID)
            videoGrants.requestCleanup()
        }
    }

    private suspend fun updatePlayback(intent: Intent?) {
        val current = matchingSession(intent?.getStringExtra(EXTRA_SESSION_ID)) ?: return
        if (current.kind != TimerKind.VIDEO || !current.isActive || current.finishCandidate != null) return
        val video = json.decodeFromString<VideoPlaybackSnapshot>(requireNotNull(intent?.getStringExtra(EXTRA_VIDEO)))
        if (video.video != current.video?.video || video.positionMillis < 0L ||
            video.speed !in listOf(0.5f, 1f, 1.25f, 1.5f, 2f)) return
        commitTransition(prepareTimerTransition(current.copy(video = video)),
            intent.getBooleanExtra(EXTRA_IMMEDIATE, false))
    }

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
                stop = ::stopCompletedCommand,
                commandStartId = handledStartId
            ).commit(transition, persist)
            pendingTransition = null
            stateHolder.setPersistenceError(false)
            stateHolder.setCommandError(null)
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

    private fun stopCompletedCommand(startId: Int) {
        if (startId <= 0 || startId == lastStopRequestId) return
        lastStopRequestId = startId
        // A newer queued request still needs this service, including its foreground notification.
        if (stopSelfResult(startId)) stopForeground(STOP_FOREGROUND_REMOVE)
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
            formatTimerClock(session.elapsedMillis)
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
        videoGrants.forget("timer-service")
        super.onDestroy()
    }

    companion object {
        const val ACTION_RESTORE = "com.risediary.app.timer.RESTORE"
        const val ACTION_START = "com.risediary.app.timer.START"
        const val ACTION_PAUSE = "com.risediary.app.timer.PAUSE"
        const val ACTION_RESUME = "com.risediary.app.timer.RESUME"
        const val ACTION_REQUEST_FINISH = "com.risediary.app.timer.REQUEST_FINISH"
        const val ACTION_CONFIRM_FINISH = "com.risediary.app.timer.CONFIRM_FINISH"
        const val ACTION_CANCEL_FINISH = "com.risediary.app.timer.CANCEL_FINISH"
        const val ACTION_PLAYBACK = "com.risediary.app.timer.PLAYBACK"
        const val EXTRA_SESSION_ID = "timer_session_id"
        const val EXTRA_START = "timer_start_request"
        const val EXTRA_WALL = "timer_wall_time"
        const val EXTRA_MONO = "timer_elapsed_realtime"
        const val EXTRA_CANDIDATE = "timer_finish_candidate"
        const val EXTRA_VIDEO = "timer_video_snapshot"
        const val EXTRA_IMMEDIATE = "timer_save_immediately"
        const val ACTION_RETRY = "com.risediary.app.timer.RETRY_PERSISTENCE"
        const val ACTION_RESET = "com.risediary.app.timer.RESET"
        const val ACTION_DISCARD = "com.risediary.app.timer.DISCARD"

        private const val NOTIFICATION_ID = 1001
        private const val MILESTONE_NOTIFICATION_ID = 1002
        private const val TICK_MILLIS = 200L


    }
}

@Suppress("InlinedApi")
internal fun foregroundServiceTypeForSdk(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }
