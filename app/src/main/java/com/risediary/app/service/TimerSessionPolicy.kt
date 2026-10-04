package com.risediary.app.service

import com.risediary.app.media.validateLocalVideoFields

internal object TimerSessionPolicy {
    fun discard(current: TimerSession, sessionId: String?): TimerSession =
        if (matches(current, sessionId)) TimerSession() else current

    fun start(current: TimerSession, request: TimerStartRequest,
              wallClockNow: Long, elapsedRealtimeNow: Long, bootCount: Int?): TimerSession {
        require(request.sessionId.isNotBlank() && request.sessionId.length <= 128)
        if (current.sessionId == request.sessionId && current.status != TimerStatus.IDLE) return current
        check(current.status == TimerStatus.IDLE) { "请先处理当前计时" }
        require((request.kind == TimerKind.VIDEO) == (request.video != null))
        request.video?.let { require(validateLocalVideoFields(it.video.uriString, it.video.displayName, it.video.mimeType) == null) }
        val wall = request.startedAtEpochMillis ?: wallClockNow
        val mono = request.startedAtElapsedRealtime ?: elapsedRealtimeNow
        require(wall > 0L && mono in 0L..elapsedRealtimeNow)
        return TimerSession(status = TimerStatus.RUNNING, sessionId = request.sessionId,
            kind = request.kind, video = request.video, startedAtEpochMillis = wall,
            resumedAtElapsedRealtime = mono, resumedAtWallClock = wall, bootCount = bootCount)
    }

    fun matches(session: TimerSession, sessionId: String?): Boolean =
        !sessionId.isNullOrBlank() && session.sessionId == sessionId
}
