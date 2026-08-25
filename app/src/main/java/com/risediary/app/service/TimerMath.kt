package com.risediary.app.service

object TimerMath {
    const val MAX_DURATION_MILLIS = 120L * 60L * 1_000L

    fun elapsed(
        session: TimerSession,
        elapsedRealtimeNow: Long,
        wallClockNow: Long
    ): Long {
        if (session.status != TimerStatus.RUNNING) return session.elapsedMillis
        val monotonicDelta = elapsedRealtimeNow - session.resumedAtElapsedRealtime
        // elapsedRealtime resets to 0 after a reboot, making the delta negative.
        // Never fall back to wall-clock here: it would count the powered-off
        // time into the duration and pollute saved records.
        val delta = if (monotonicDelta >= 0L) monotonicDelta else 0L
        return (session.elapsedMillis + delta).coerceIn(0L, MAX_DURATION_MILLIS)
    }

    /** Moves a running session's baseline forward after a periodic tick. */
    fun advance(
        session: TimerSession,
        elapsedRealtimeNow: Long,
        wallClockNow: Long
    ): TimerSession {
        if (session.status != TimerStatus.RUNNING) return session
        return session.copy(
            elapsedMillis = elapsed(session, elapsedRealtimeNow, wallClockNow),
            resumedAtElapsedRealtime = elapsedRealtimeNow,
            resumedAtWallClock = wallClockNow
        )
    }
}

enum class TimerMilestone(val minutes: Int, val bit: Int) {
    THIRTY(30, 1 shl 0),
    SIXTY(60, 1 shl 1),
    NINETY(90, 1 shl 2),
    LIMIT(120, 1 shl 3);

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
