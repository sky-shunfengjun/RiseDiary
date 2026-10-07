package com.risediary.app.data.repository

import com.risediary.app.data.dao.FlightDao
import com.risediary.app.data.entity.Flight
import com.risediary.app.util.LocalCalendarContext
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeHistoryTagsTest {
    @Test fun aFifthTagInTheOldestRecordIsIncludedBeyondOneThousandRecords() = runTest {
        val latest = List(1000) { index -> flight(index + 2L, "[\"tag${index % 4}\"]") }
        val tags = repository(latest + flight(1L, "[\"fifth\"]")).getAllMethodTags()
        assertEquals(1001, tags.size)
        assertTrue("An older or backdated record must count toward lifetime tags", "[\"fifth\"]" in tags)
    }
    @Test fun queryingAnEmptyHistoryReturnsNoTagRows() = runTest {
        assertEquals(emptyList<String>(), repository(emptyList()).getAllMethodTags())
    }
    private fun repository(records: List<Flight>): RoomFlightRepository {
        val dao = Proxy.newProxyInstance(FlightDao::class.java.classLoader, arrayOf(FlightDao::class.java)) { _, method, args ->
            when (method.name) {
                "getAllFlow" -> flowOf(records)
                "getRecent" -> records.take(args!![0] as Int)
                "getAllMethodTags" -> records.map(Flight::methodTags)
                else -> throw AssertionError("Unexpected DAO call: ${method.name}")
            }
        } as FlightDao
        return RoomFlightRepository(dao, LocalCalendarContext(Clock.systemUTC()))
    }
    private fun flight(id: Long, tags: String) = Flight(
        id = id, startTime = id * 100_000L, endTime = id * 100_000L + 60_000L,
        durationSeconds = 60, spurtCount = null, semenVolumeMl = 1f,
        ejaculationDistanceCm = null, methodTags = tags, moodNote = "",
        createdAt = 1_000L, updatedAt = 1_000L
    )
}