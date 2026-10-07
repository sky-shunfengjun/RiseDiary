package com.risediary.app.service

import com.risediary.app.util.DurationPolicy

object TimerMath {
    const val MAX_DURATION_MILLIS = DurationPolicy.MAX_MILLIS

    fun elapsed(
        session: TimerSession,
        elapsedRealtimeNow: Long,
        wallClockNow: Long,
        currentBootCount: Int? = null
    ): Long {
        if (session.bootCount == null || session.bootCount < 0 || currentBootCount != session.bootCount) {
            return session.elapsedMillis.coerceIn(0L, MAX_DURATION_MILLIS)
        }
        return elapsedInCurrentProcess(session, elapsedRealtimeNow)
    }

    /** Only for a session created here, or normalized by [restore] before it entered the holder. */
    internal fun elapsedInCurrentProcess(session: TimerSession, elapsedRealtimeNow: Long): Long {
        if (session.status != TimerStatus.RUNNING) return session.elapsedMillis
        val delta = (elapsedRealtimeNow - session.resumedAtElapsedRealtime).coerceAtLeast(0L)
        return (session.elapsedMillis.coerceIn(0L, MAX_DURATION_MILLIS) +
            delta.coerceAtMost(MAX_DURATION_MILLIS)).coerceAtMost(MAX_DURATION_MILLIS)
    }

    internal fun restore(
        session: TimerSession,
        elapsedRealtimeNow: Long,
        wallClockNow: Long,
        currentBootCount: Int?
    ): TimerSession {
        if (session.status != TimerStatus.RUNNING) return session
        val sameBoot = currentBootCount != null && currentBootCount >= 0 &&
            session.bootCount == currentBootCount &&
            elapsedRealtimeNow >= session.resumedAtElapsedRealtime
        val advanced = if (sameBoot) advance(session, elapsedRealtimeNow, wallClockNow) else session
        val elapsed = advanced.elapsedMillis.coerceIn(0L, MAX_DURATION_MILLIS)
        return advanced.copy(
            status = if (sameBoot) TimerStatus.RUNNING else TimerStatus.PAUSED,
            elapsedMillis = elapsed,
            resumedAtElapsedRealtime = if (sameBoot && elapsed < MAX_DURATION_MILLIS) elapsedRealtimeNow else 0L,
            resumedAtWallClock = if (sameBoot && elapsed < MAX_DURATION_MILLIS) wallClockNow else 0L,
            bootCount = currentBootCount
        )
    }
    /** Moves a running session's baseline forward after a periodic tick. */
    fun advance(
        session: TimerSession,
        elapsedRealtimeNow: Long,
        wallClockNow: Long
    ): TimerSession {
        if (session.status != TimerStatus.RUNNING) return session
        val elapsed = elapsedInCurrentProcess(session, elapsedRealtimeNow)
        val crossedLimit = session.elapsedMillis < MAX_DURATION_MILLIS && elapsed >= MAX_DURATION_MILLIS
        val excess = if (crossedLimit) ((elapsedRealtimeNow - session.resumedAtElapsedRealtime).coerceAtLeast(0L) -
            (MAX_DURATION_MILLIS - session.elapsedMillis)).coerceAtLeast(0L) else 0L
        return session.copy(
            endedAtEpochMillis = session.endedAtEpochMillis ?: if (crossedLimit) wallClockNow - excess else null,
            elapsedMillis = elapsed,
            resumedAtElapsedRealtime = elapsedRealtimeNow,
            resumedAtWallClock = wallClockNow
        )
    }
}

enum class TimerMilestone(val minutes: Int, val bit: Int) {
    THIRTY(30, 1 shl 0),
    SIXTY(60, 1 shl 1),
    NINETY(90, 1 shl 2),
    LIMIT(DurationPolicy.MAX_SECONDS / 60, 1 shl 3);

    val elapsedMillis: Long
        get() = minutes * 60_000L
}

object TimerMilestones {
    fun latestUnnotified(elapsedMillis: Long, notifiedMask: Int): TimerMilestone? =
        TimerMilestone.entries.lastOrNull { milestone ->
            elapsedMillis >= milestone.elapsedMillis &&
                notifiedMask and milestone.bit == 0
        }

    fun maskThrough(elapsedMillis: Long): Int =
        TimerMilestone.entries
            .filter { elapsedMillis >= it.elapsedMillis }
            .fold(0) { mask, milestone -> mask or milestone.bit }
}
