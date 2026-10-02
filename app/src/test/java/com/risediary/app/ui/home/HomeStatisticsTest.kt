package com.risediary.app.ui.home

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.util.LocalCalendarSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HomeStatisticsTest {
    private val now = Instant.parse("2026-10-01T18:00:00Z").toEpochMilli()
    private val flight = Flight(1, now, now + 600_000, 600, 10, 20f,
        ejaculationDistanceCm = 30f, methodTags = "[]", moodNote = "")

    @Test fun deletionAndUndoRecalculateEveryFieldFromSameDataSnapshot() {
        val calendar = LocalCalendarSnapshot(LocalDate.parse("2026-10-02"), ZoneId.of("Asia/Shanghai"), 0)
        val before = HomeStatistics.calculate(listOf(flight), emptyList(), calendar, now)
        val deleted = HomeStatistics.calculate(emptyList(), emptyList(), calendar, now)
        val restored = HomeStatistics.calculate(listOf(flight), emptyList(), calendar, now)
        assertEquals(1, before.todayCount)
        assertEquals(20f, before.weekVolumeSum, 0f)
        assertEquals(0, deleted.todayCount)
        assertEquals(0, deleted.totalCount)
        assertEquals(0f, deleted.avgDuration, 0f)
        assertEquals(emptyMap<String, Int>(), deleted.flightCountsByDay)
        assertEquals(before, restored)
    }

    @Test fun midnightAndTimezoneUseSuppliedSnapshotForCountsHeatmapAndLastRecord() {
        val shanghai = LocalCalendarSnapshot(LocalDate.parse("2026-10-03"), ZoneId.of("Asia/Shanghai"), 1)
        val la = LocalCalendarSnapshot(LocalDate.parse("2026-10-01"), ZoneId.of("America/Los_Angeles"), 2)
        val china = HomeStatistics.calculate(listOf(flight), emptyList(), shanghai, now)
        val america = HomeStatistics.calculate(listOf(flight), emptyList(), la, now)
        assertEquals(0, china.todayCount)
        assertEquals(1, china.lastFlightDaysAgo)
        assertEquals(mapOf("2026-10-02" to 1), china.flightCountsByDay)
        assertEquals(1, america.todayCount)
        assertEquals(0, america.lastFlightDaysAgo)
        assertEquals(mapOf("2026-10-01" to 1), america.flightCountsByDay)
    }

    @Test fun currentMonthLengthUsesCalendarZoneNotUtcMonth() {
        val calendar = LocalCalendarSnapshot(LocalDate.parse("2026-10-01"), ZoneId.of("Asia/Shanghai"), 0)
        val septemberUtc = Instant.parse("2026-09-30T18:00:00Z").toEpochMilli()
        val length = LengthRecord(1, septemberUtc, 5f, 10f)
        val stats = HomeStatistics.calculate(emptyList(), listOf(length), calendar, now)
        assertEquals(length, stats.currentMonthLength)
    }
}
