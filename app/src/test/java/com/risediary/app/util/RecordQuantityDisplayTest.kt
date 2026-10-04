package com.risediary.app.util

import com.risediary.app.data.entity.Flight
import org.junit.Assert.*
import org.junit.Test

class RecordQuantityDisplayTest {
    private fun record() = Flight(startTime=1000, endTime=2000, durationSeconds=1,
        spurtCount=null, semenVolumeMl=2.3f, ejaculationDistanceCm=null, methodTags="[]", moodNote="")
    @Test fun predictionIsMarkedAsApproximate() {
        assertEquals("约 2.3 毫升", RecordQuantityDisplay.current(record().copy(volumeInputMode="estimated")))
        assertEquals("2.3 毫升", RecordQuantityDisplay.current(record()))
    }
    @Test fun missingHistoricalMillilitersAreNotDisplayedAsZeroOrRecalculated() {
        assertEquals("3 股 · 毫升未记录", RecordQuantityDisplay.current(record().copy(spurtCount=3, semenVolumeMl=null, volumeInputMode="spurts")))
    }
    @Test fun historicalQuantityDisplaysStoredMillilitersRegardlessOfOldInputMode() {
        val old = record().copy(spurtCount = 3, semenVolumeMl = 1.5f)
        assertEquals("1.5 毫升", RecordQuantityDisplay.current(old))
        assertEquals("1.5 毫升", RecordQuantityDisplay.current(old.copy(volumeInputMode = "spurts")))
    }
    @Test fun archiveIsDisplayedSeparatelyFromCurrentValue() {
        val edited = record().copy(legacySpurtCount=3, legacyVolumeMl=null, legacyVolumeInputMode="spurts")
        assertEquals("2.3 毫升", RecordQuantityDisplay.current(edited))
        assertEquals("3 股 · 毫升未记录", RecordQuantityDisplay.original(edited))
        assertNull(RecordQuantityDisplay.original(record()))
    }
}
