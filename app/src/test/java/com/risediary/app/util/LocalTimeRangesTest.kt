package com.risediary.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LocalTimeRangesTest {
    @Test
    fun shanghaiDayUsesLocalMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val (start, end) = LocalTimeRanges.day(LocalDate.of(2026, 7, 18), zone)

        assertEquals(
            Instant.parse("2026-07-17T16:00:00Z").toEpochMilli(),
            start
        )
        assertEquals(Duration.ofHours(24).toMillis(), end - start)
    }

    @Test
    fun negativeTimezoneDayHandlesDaylightSavingTransition() {
        val zone = ZoneId.of("America/Los_Angeles")
        val (start, end) = LocalTimeRanges.day(LocalDate.of(2024, 3, 10), zone)

        assertEquals(Duration.ofHours(23).toMillis(), end - start)
    }

    @Test
    fun weekAlwaysStartsOnMonday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val (start, end) = LocalTimeRanges.weekContaining(
            LocalDate.of(2026, 7, 18),
            zone
        )

        assertEquals(LocalDate.of(2026, 7, 13), LocalTimeRanges.localDate(start, zone))
        assertEquals(LocalDate.of(2026, 7, 20), LocalTimeRanges.localDate(end, zone))
    }
}
