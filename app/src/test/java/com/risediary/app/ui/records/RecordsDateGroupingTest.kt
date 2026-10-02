package com.risediary.app.ui.records

import com.risediary.app.data.entity.Flight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RecordsDateGroupingTest {
    private val time = Instant.parse("2026-10-01T18:00:00Z").toEpochMilli()
    private val flight = Flight(1, time, time + 60_000, 60, 2, 4f,
        ejaculationDistanceCm = null, methodTags = "[]", moodNote = "")

    @Test fun nextDayReplacesTodayLabelWithoutChangingRecords() {
        val zone = ZoneId.of("Asia/Shanghai")
        assertEquals("Today", groupByDate(listOf(flight), zone, LocalDate.parse("2026-10-02"), "Today", "Yesterday").single().header)
        assertEquals("Yesterday", groupByDate(listOf(flight), zone, LocalDate.parse("2026-10-03"), "Today", "Yesterday").single().header)
    }

    @Test fun timezoneReassignsExistingTimestampToItsNewLocalDay() {
        val day = LocalDate.parse("2026-10-02")
        assertEquals("Yesterday", groupByDate(listOf(flight), ZoneId.of("America/Los_Angeles"), day, "Today", "Yesterday").single().header)
    }
}
