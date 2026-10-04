package com.risediary.app.service

import com.risediary.app.media.VideoPlaybackSnapshot
import kotlinx.serialization.Serializable

@Serializable
enum class TimerStatus { IDLE, RUNNING, PAUSED, FINISHED, LIMIT_REACHED }
@Serializable
enum class TimerKind { NORMAL, VIDEO }

@Serializable
data class TimerStartRequest(
    val sessionId: String,
    val kind: TimerKind,
    val video: VideoPlaybackSnapshot? = null,
    val startedAtEpochMillis: Long? = null,
    val startedAtElapsedRealtime: Long? = null
)

@Serializable
data class TimerFinishCandidate(
    val sessionId: String,
    val requestedAtEpochMillis: Long,
    val elapsedMillis: Long,
    val priorStatus: TimerStatus
)

@Serializable
data class TimerSession(
    val status: TimerStatus = TimerStatus.IDLE,
    val startedAtEpochMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val resumedAtElapsedRealtime: Long = 0L,
    val resumedAtWallClock: Long = 0L,
    val notifiedMilestonesMask: Int = 0,
    val bootCount: Int? = null,
    val sessionId: String? = null,
    val kind: TimerKind = TimerKind.NORMAL,
    val video: VideoPlaybackSnapshot? = null,
    val finishCandidate: TimerFinishCandidate? = null,
    val endedAtEpochMillis: Long? = null,
    val liveUpdateDismissed: Boolean = false
) {
    val isActive: Boolean get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED
    val isTerminal: Boolean get() = status == TimerStatus.FINISHED || status == TimerStatus.LIMIT_REACHED
}
