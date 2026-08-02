package com.risediary.app.service

import android.os.SystemClock
import javax.inject.Inject

interface ElapsedRealtimeClock {
    fun millis(): Long
}

class SystemElapsedRealtimeClock @Inject constructor() : ElapsedRealtimeClock {
    override fun millis(): Long = SystemClock.elapsedRealtime()
}
