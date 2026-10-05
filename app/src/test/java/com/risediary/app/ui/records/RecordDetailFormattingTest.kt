package com.risediary.app.ui.records

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordDetailFormattingTest {
    @Test fun crossingMidnightKeepsDatesOnBothStartAndEnd() {
        val zone = ZoneId.of("Asia/Shanghai")
        assertEquals("2026-10-05 23:59:30", formatDetailDateTime(Instant.parse("2026-10-05T15:59:30Z").toEpochMilli(), zone))
        assertEquals("2026-10-06 00:01:30", formatDetailDateTime(Instant.parse("2026-10-05T16:01:30Z").toEpochMilli(), zone))
    }
}
