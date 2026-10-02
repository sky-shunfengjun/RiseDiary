package com.risediary.app.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LocalCalendarContextTest {
    @Test fun timezoneChangesEvenWhenBothZonesHaveSameDate() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-02T05:00:00Z"))
        var zone = ZoneId.of("Asia/Shanghai")
        val calendar = LocalCalendarContext(clock, { zone }, backgroundScope)
        val before = calendar.current()
        zone = ZoneId.of("Asia/Tokyo")
        val after = calendar.current()
        assertEquals(before.date, after.date)
        assertEquals(zone, after.zoneId)
        assertTrue(after.revision > before.revision)
    }

    @Test fun nextLocalMidnightHonorsBothDstDayLengths() = runTest {
        val zone = ZoneId.of("America/Los_Angeles")
        val clock = MutableClock(Instant.parse("2024-03-10T08:00:00Z"))
        val calendar = LocalCalendarContext(clock, { zone }, backgroundScope)
        assertEquals(23 * 60 * 60 * 1000L, calendar.millisUntilNextMidnight())
        clock.now = Instant.parse("2024-11-03T07:00:00Z")
        assertEquals(25 * 60 * 60 * 1000L, calendar.millisUntilNextMidnight())
    }

    @Test fun foregroundMidnightRecalibratesAndBackgroundStopsWaiting() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-01T15:59:59Z"))
        val calendar = LocalCalendarContext(clock, { ZoneId.of("Asia/Shanghai") }, backgroundScope)
        calendar.setForeground(true)
        runCurrent()
        clock.now = Instant.parse("2026-10-01T16:00:00Z")
        advanceTimeBy(1000)
        runCurrent()
        assertEquals("2026-10-02", calendar.state.value.date.toString())
        calendar.setForeground(false)
        clock.now = Instant.parse("2026-10-02T16:00:00Z")
        advanceTimeBy(86_400_000)
        runCurrent()
        assertEquals("2026-10-02", calendar.state.value.date.toString())
        calendar.setForeground(true)
        assertEquals("2026-10-03", calendar.state.value.date.toString())
    }

    @Test fun unchangedResumeDoesNotInvalidateButWallClockBroadcastDoes() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-02T05:00:00Z"))
        val calendar = LocalCalendarContext(clock, { ZoneId.of("Asia/Shanghai") }, backgroundScope)
        val revision = calendar.current().revision
        calendar.setForeground(true)
        calendar.setForeground(false)
        calendar.setForeground(true)
        assertEquals(revision, calendar.state.value.revision)
        calendar.onSystemTimeChanged()
        assertTrue(calendar.state.value.revision > revision)
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
