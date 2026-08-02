package com.risediary.app.service

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
    LIMIT_REACHED
}

data class TimerSession(
    val status: TimerStatus = TimerStatus.IDLE,
    val startedAtEpochMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val resumedAtElapsedRealtime: Long = 0L,
    val resumedAtWallClock: Long = 0L,
    val notifiedMilestonesMask: Int = 0
) {
    val isActive: Boolean
        get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED
}
