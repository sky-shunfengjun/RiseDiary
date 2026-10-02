package com.risediary.app.update

/** Monotonic timestamps only; the counter lives with the About destination. */
internal class DeveloperTapCounter {
    private var count = 0
    private var lastTap: Long? = null
    fun tap(nowMillis: Long): Boolean {
        val previous = lastTap
        if (previous == null || nowMillis < previous || nowMillis - previous > 3_000) count = 0
        lastTap = nowMillis
        count++
        return if (count == 20) { reset(); true } else false
    }
    fun reset() { count = 0; lastTap = null }
}

internal fun isDeveloperPasswordValid(value: String): Boolean = value == "1095102874"