package com.risediary.app.ui.components

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDataTransformTest {

    @Test
    fun trendPoints_keepZero_sortByTime_andExcludeOnlyMissingOrInvalidValues() {
        val base = localNoon(2026, Calendar.JULY, 25)
        val flights = listOf(
            flight(id = 3, time = base + 3_600_000L, volume = null),
            flight(id = 2, time = base + 1_800_000L, volume = 2.5f),
            flight(id = 1, time = base, volume = 0f),
            flight(id = 4, time = base + 5_400_000L, volume = Float.NaN)
        )

        val points = buildTrendChartPoints(flights, isVolume = true, locale = Locale.US)

        assertEquals(listOf(0f, 2.5f), points.map(TrendChartPoint::value))
        assertEquals(listOf(0, 1), points.map(TrendChartPoint::x))
        assertTrue(points.all { ":" in it.axisLabel })
        assertTrue(points[0].markerLabel != points[1].markerLabel)
    }

    @Test
    fun trendPoints_doNotMergeMultipleRecordsFromTheSameDay() {
        val base = localNoon(2026, Calendar.JULY, 25)
        val points = buildTrendChartPoints(
            flights = listOf(
                flight(id = 1, time = base, distance = 10f),
                flight(id = 2, time = base + 60_000L, distance = 30f)
            ),
            isVolume = false,
            locale = Locale.US
        )

        assertEquals(2, points.size)
        assertEquals(listOf(10f, 30f), points.map(TrendChartPoint::value))
        assertTrue(points[0].axisLabel != points[1].axisLabel)
    }

    @Test
    fun lengthPoints_keepZero_sortAndDistinguishSameDayRecords() {
        val base = localNoon(2026, Calendar.JULY, 25)
        val points = buildLengthChartPoints(
            records = listOf(
                LengthRecord(id = 2, recordDate = base, flaccidLengthCm = 5f, erectLengthCm = 9f),
                LengthRecord(id = 1, recordDate = base, flaccidLengthCm = 0f, erectLengthCm = 0f),
                LengthRecord(
                    id = 3,
                    recordDate = base + 86_400_000L,
                    flaccidLengthCm = Float.NaN,
                    erectLengthCm = 12f
                )
            ),
            locale = Locale.US
        )

        assertEquals(2, points.size)
        assertEquals(listOf(0f, 9f), points.map(LengthChartPoint::erect))
        assertTrue(points[0].axisLabel.endsWith("· 1"))
        assertTrue(points[1].axisLabel.endsWith("· 2"))
    }

    @Test
    fun trendAxisRange_usesReadableStepsAndKeepsHeadroom() {
        val range = trendAxisRange(listOf(5.0, 10.0))

        assertEquals(0.0, range.minY, 0.0)
        assertEquals(2.5, range.step, 0.0)
        assertEquals(12.5, range.maxY, 0.0)
    }

    @Test
    fun lengthAxisRange_avoidsFractionalCountBasedTicks() {
        val range = lengthAxisRange(listOf(5.0, 8.0, 9.0, 20.0))

        assertEquals(0.0, range.minY, 0.0)
        assertEquals(5.0, range.step, 0.0)
        assertEquals(25.0, range.maxY, 0.0)
    }

    @Test
    fun lengthAxisRange_handlesEqualAndZeroValues() {
        val equalRange = lengthAxisRange(listOf(10.0, 10.0))
        val zeroRange = lengthAxisRange(listOf(0.0, 0.0))

        assertTrue(equalRange.maxY > equalRange.minY)
        assertTrue(equalRange.step > 0.0)
        assertEquals(0.0, zeroRange.minY, 0.0)
        assertTrue(zeroRange.maxY > 0.0)
    }

    @Test
    fun chartMarkers_showValuesWithoutRepeatingDates() {
        assertEquals("12.5 ml", formatTrendMarkerValue(12.5, "ml"))
        assertEquals(
            "勃起 9 cm\n疲软 5.5 cm",
            formatLengthMarkerValue("勃起 %1\$s cm\n疲软 %2\$s cm", 9.0, 5.5)
        )
    }

    @Test
    fun chartAxisLabel_clampsStaleIndexesAfterRecordDeletion() {
        val labelsAfterDeletion = listOf("7/25")

        assertEquals("7/25", formatChartAxisLabel(labelsAfterDeletion, 1.0))
        assertEquals("7/25", formatChartAxisLabel(labelsAfterDeletion, -1.0))
        assertEquals("7/25", formatChartAxisLabel(labelsAfterDeletion, Double.NaN))
        assertEquals("—", formatChartAxisLabel(emptyList(), 0.0))
        assertEquals("—", formatChartAxisLabel(listOf(""), 0.0))
    }

    private fun flight(
        id: Long,
        time: Long,
        volume: Float? = null,
        distance: Float? = null
    ) = Flight(
        id = id,
        startTime = time,
        endTime = time + 60_000L,
        durationSeconds = 60,
        spurtCount = null,
        semenVolumeMl = volume,
        ejaculationDistanceCm = distance,
        methodTags = "",
        moodNote = ""
    )

    private fun localNoon(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis
}
