package com.risediary.app.service

internal enum class TimerNotificationAction { PAUSE, RESUME, FINISH }
internal data class TimerNotificationCapabilities(
    val sdkInt: Int,
    val notificationsAllowed: Boolean = true,
    val promotionAllowed: Boolean = true,
    val channelImportance: Int = 2
)
internal data class TimerNotificationUpdateKey(
    val sessionId: String?,
    val status: TimerStatus,
    val candidate: TimerFinishCandidate?,
    val frozenElapsed: Long?,
    val runningBase: Long?,
    val promotion: Boolean,
    val commandFailed: Boolean
)

internal object TimerNotificationPolicy {
    fun supportsLiveUpdates(sdkInt: Int): Boolean = sdkInt >= 36

    fun requestPromotion(session: TimerSession, enabled: Boolean, caps: TimerNotificationCapabilities,
                         dismissed: Boolean = false, commandFailed: Boolean = false): Boolean =
        enabled && supportsLiveUpdates(caps.sdkInt) && caps.notificationsAllowed &&
            caps.promotionAllowed && caps.channelImportance >= 2 && session.isActive &&
            !session.sessionId.isNullOrBlank() && session.finishCandidate == null && !dismissed && !commandFailed

    fun actions(session: TimerSession, commandFailed: Boolean = false): List<TimerNotificationAction> =
        if (commandFailed || session.sessionId.isNullOrBlank() || !session.isActive ||
            session.finishCandidate != null) emptyList()
        else listOf(if (session.status == TimerStatus.PAUSED) TimerNotificationAction.RESUME
            else TimerNotificationAction.PAUSE, TimerNotificationAction.FINISH)

    fun elapsed(session: TimerSession, monotonicNow: Long): Long =
        session.finishCandidate?.elapsedMillis ?: TimerMath.elapsedInCurrentProcess(session, monotonicNow)

    fun chronometerWhen(session: TimerSession, monotonicNow: Long, wallNow: Long): Long =
        (wallNow - elapsed(session, monotonicNow)).coerceAtLeast(1L)

    fun updateKey(session: TimerSession, promotion: Boolean, commandFailed: Boolean): TimerNotificationUpdateKey {
        val running = session.status == TimerStatus.RUNNING && session.finishCandidate == null
        return TimerNotificationUpdateKey(session.sessionId, session.status, session.finishCandidate,
            if (running) null else session.finishCandidate?.elapsedMillis ?: session.elapsedMillis,
            if (running) session.resumedAtElapsedRealtime - session.elapsedMillis else null,
            promotion, commandFailed)
    }

    fun dismiss(session: TimerSession, expectedId: String?): TimerSession =
        if (session.isActive && TimerSessionPolicy.matches(session, expectedId))
            session.copy(liveUpdateDismissed = true) else session
}
