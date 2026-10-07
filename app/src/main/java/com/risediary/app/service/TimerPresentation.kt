package com.risediary.app.service

/** UI controls depend on lifecycle/finish state, not the clock or playback polling. */
fun TimerSession.forControls(): TimerSession = copy(elapsedMillis = 0L, resumedAtElapsedRealtime = 0L,
    resumedAtWallClock = 0L, video = video?.copy(positionMillis = 0L))
