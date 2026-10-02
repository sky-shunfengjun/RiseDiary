package com.risediary.app.data.repository

import com.risediary.app.data.dao.FlightDao
import com.risediary.app.util.LocalCalendarContext
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class CalendarRepositoryTest {
    @Test fun changingSystemTimezoneChangesTodayRangeWithoutRestartingRepository() {
        val oldDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val instant = Instant.parse("2026-10-01T18:00:00Z")
            val clock = Clock.fixed(instant, ZoneId.systemDefault())
            val dao = Proxy.newProxyInstance(
                FlightDao::class.java.classLoader, arrayOf(FlightDao::class.java)
            ) { _, method, _ ->
                if (method.name == "getAllFlow") flowOf(emptyList<Any>())
                else error("Unexpected DAO call: ${method.name}")
            } as FlightDao
            val repo = RoomFlightRepository(dao, LocalCalendarContext(clock))
            assertEquals(Instant.parse("2026-10-01T16:00:00Z").toEpochMilli(), repo.todayRange().first)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(Instant.parse("2026-10-01T07:00:00Z").toEpochMilli(), repo.todayRange().first)
        } finally {
            TimeZone.setDefault(oldDefault)
        }
    }
}
