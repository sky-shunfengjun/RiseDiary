package com.risediary.app.service

/** Capturing is done at the UI/intent boundary, before asynchronous storage or dialog work. */
internal object TimerFinishPolicy {
    fun capture(session: TimerSession, wallClockNow: Long, elapsedRealtimeNow: Long): TimerFinishCandidate {
        session.finishCandidate?.let { return it }
        require(session.isActive)
        return TimerFinishCandidate(requireNotNull(session.sessionId), wallClockNow,
            TimerMath.elapsedInCurrentProcess(session, elapsedRealtimeNow).coerceIn(0L, TimerMath.MAX_DURATION_MILLIS),
            session.status)
    }

    /** Merge a captured finish into the newest checkpoint, including an unpublished failed write. */
    fun attach(session: TimerSession, candidate: TimerFinishCandidate): TimerSession {
        if (session.finishCandidate != null || session.status == TimerStatus.FINISHED ||
            session.status == TimerStatus.IDLE) return session
        require(candidate.sessionId == session.sessionId && candidate.requestedAtEpochMillis > 0L)
        require(candidate.elapsedMillis in 0L..TimerMath.MAX_DURATION_MILLIS &&
            candidate.priorStatus in setOf(TimerStatus.RUNNING, TimerStatus.PAUSED))
        return session.copy(finishCandidate = candidate,
            status = if (session.status == TimerStatus.LIMIT_REACHED) candidate.priorStatus else session.status)
    }

    fun confirm(session: TimerSession): TimerSession {
        if (session.isTerminal && session.finishCandidate == null) return session
        val candidate = requireNotNull(session.finishCandidate)
        require(candidate.sessionId == session.sessionId && candidate.priorStatus in
            setOf(TimerStatus.RUNNING, TimerStatus.PAUSED))
        require(candidate.elapsedMillis in 0L..TimerMath.MAX_DURATION_MILLIS)
        return session.copy(status = if (candidate.elapsedMillis == TimerMath.MAX_DURATION_MILLIS)
            TimerStatus.LIMIT_REACHED else TimerStatus.FINISHED,
            elapsedMillis = candidate.elapsedMillis, endedAtEpochMillis = candidate.requestedAtEpochMillis,
            resumedAtElapsedRealtime = 0L, resumedAtWallClock = 0L, finishCandidate = null)
    }

    fun cancel(session: TimerSession, wallClockNow: Long, elapsedRealtimeNow: Long): TimerSession {
        if (session.finishCandidate == null) return session
        require(session.finishCandidate.sessionId == session.sessionId)
        // A reboot may have safely restored RUNNING as PAUSED; never resume it implicitly.
        val current = if (session.status == TimerStatus.RUNNING)
            TimerMath.advance(session, elapsedRealtimeNow, wallClockNow) else session
        return prepareTimerTransition(current.copy(finishCandidate = null)).session
    }
}
