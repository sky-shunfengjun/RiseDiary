package com.risediary.app.util

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LocalCalendarSnapshot(val date: LocalDate, val zoneId: ZoneId, val revision: Long)

/** Wall-clock instants and the phone's current calendar are deliberately separate. */
@Singleton
class LocalCalendarContext internal constructor(
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId,
    private val scope: CoroutineScope
) {
    @Inject constructor(clock: Clock) : this(
        clock, { ZoneId.systemDefault() }, CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    private val lock = Any()
    private val initialZone = zoneProvider()
    private val _state = MutableStateFlow(
        LocalCalendarSnapshot(clock.instant().atZone(initialZone).toLocalDate(), initialZone, 0)
    )
    val state: StateFlow<LocalCalendarSnapshot> = _state.asStateFlow()
    private var foreground = false
    private var midnightJob: Job? = null

    fun current(): LocalCalendarSnapshot = synchronized(lock) { calibrate(false) }

    fun setForeground(value: Boolean) = synchronized(lock) {
        foreground = value
        calibrate(false)
        scheduleMidnight()
    }

    fun onSystemTimeChanged() = synchronized(lock) {
        calibrate(true)
        scheduleMidnight()
    }

    internal fun millisUntilNextMidnight(): Long {
        val now = clock.instant()
        val zone = zoneProvider()
        val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, next).toMillis().coerceAtLeast(1)
    }

    private fun calibrate(force: Boolean): LocalCalendarSnapshot {
        val zone = zoneProvider()
        val date = clock.instant().atZone(zone).toLocalDate()
        val previous = _state.value
        if (force || previous.date != date || previous.zoneId != zone) {
            _state.value = LocalCalendarSnapshot(date, zone, previous.revision + 1)
        }
        return _state.value
    }

    private fun scheduleMidnight() {
        midnightJob?.cancel()
        midnightJob = null
        if (!foreground) return
        midnightJob = scope.launch {
            while (isActive) {
                delay(millisUntilNextMidnight())
                synchronized(lock) { calibrate(false) }
            }
        }
    }
}
