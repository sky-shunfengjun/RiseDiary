package com.risediary.app.service

internal const val TIMER_CHECKPOINT_MILLIS = 15_000L

internal data class TimerTransition(val session: TimerSession, val milestone: TimerMilestone?)

internal fun prepareTimerTransition(session: TimerSession): TimerTransition {
    val elapsed = session.elapsedMillis.coerceIn(0L, TimerMath.MAX_DURATION_MILLIS)
    val reachedLimit = elapsed >= TimerMath.MAX_DURATION_MILLIS
    return TimerTransition(
        session.copy(
            status = if (reachedLimit) TimerStatus.LIMIT_REACHED else session.status,
            elapsedMillis = elapsed,
            resumedAtElapsedRealtime = if (reachedLimit) 0L else session.resumedAtElapsedRealtime,
            resumedAtWallClock = if (reachedLimit) 0L else session.resumedAtWallClock,
            notifiedMilestonesMask = session.notifiedMilestonesMask or TimerMilestones.maskThrough(elapsed)
        ),
        TimerMilestones.latestUnnotified(elapsed, session.notifiedMilestonesMask)
    )
}

internal fun shouldPersistTimerTick(previous: TimerSession, next: TimerSession, now: Long, lastSaved: Long): Boolean =
    next.status == TimerStatus.LIMIT_REACHED ||
        previous.notifiedMilestonesMask != next.notifiedMilestonesMask ||
        now - lastSaved >= TIMER_CHECKPOINT_MILLIS

/** Used by every service command; no terminal side effect happens before the durable state. */
internal class TimerSessionCommitter(
    private val save: suspend (TimerSession) -> Unit,
    private val publish: (TimerSession) -> Unit,
    private val notify: (TimerMilestone) -> Unit,
    private val stop: () -> Unit
) {
    suspend fun commit(transition: TimerTransition, persist: Boolean) {
        val terminal = transition.session.status in setOf(TimerStatus.IDLE, TimerStatus.FINISHED, TimerStatus.LIMIT_REACHED)
        if (persist || terminal) save(transition.session)
        publish(transition.session)
        transition.milestone?.let(notify)
        if (terminal) stop()
    }
}