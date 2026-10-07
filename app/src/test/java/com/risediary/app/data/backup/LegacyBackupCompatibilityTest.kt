package com.risediary.app.data.backup

import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.sync.RecordIdentity
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

/** Fixture schema is audited against actual v1.0.0-beta through v1.1.2 exporters. */
class LegacyBackupCompatibilityTest {
    private fun fixture(name: String): String = checkNotNull(javaClass.getResourceAsStream("/backup-v1/$name"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test fun oldQuantityAndHistoryArePreservedWithoutRecalculation() {
        val rows = BackupJsonCodec.parseFlights(fixture("flights.json"))
        val old = rows.single { it.id == 9L }
        assertEquals(3, old.spurtCount)
        assertEquals(5.25f, old.semenVolumeMl!!, 0f)
        assertEquals(3, old.legacySpurtCount)
        assertEquals(5.25f, old.legacyVolumeMl!!, 0f)
        assertEquals("spurts", old.legacyVolumeInputMode)
        val countOnly = rows.single { it.id == 13L }
        assertEquals(2, countOnly.spurtCount)
        assertNull(countOnly.semenVolumeMl)
        assertNull(countOnly.legacyVolumeMl)
        val ml = rows.single { it.id == 2L }
        assertNull(ml.spurtCount)
        assertEquals(8.25f, ml.semenVolumeMl!!, 0f)
    }

    @Test fun oldRecordTimesTextAndMissingNewFieldsRemainCompatible() {
        val row = BackupJsonCodec.parseFlights(fixture("flights.json")).single { it.id == 9L }
        assertEquals(1700000000000, row.startTime)
        assertEquals(1700000120000, row.endTime)
        assertEquals(120, row.durationSeconds)
        assertEquals(1700000001000, row.createdAt)
        assertEquals(1700000003000, row.updatedAt)
        assertEquals("旧版备注\n第二行 😀", row.moodNote)
        assertEquals("[\"原标签\",\"测试方式\"]", row.methodTags)
        assertTrue(RecordIdentity.isValidId(row.globalId))
        assertEquals("phone", row.recordSource)
        assertEquals("manual", row.timingSource)
        assertNull(row.sourceDeviceId); assertNull(row.recordDraftId)
        assertNull(row.predictionMaxTicks); assertNull(row.videoUri)
    }

    @Test fun legacyLengthsTagsAndAchievementsKeepAllOriginalValues() {
        val length = BackupJsonCodec.parseLengths(fixture("length_records.json")).single()
        assertTrue(RecordIdentity.isValidId(length.globalId))
        assertEquals(1700000000000, length.recordDate)
        assertEquals(7.5f, length.flaccidLengthCm, 0f)
        assertEquals(12.25f, length.erectLengthCm, 0f)
        assertEquals("旧长度备注 😀", length.note)
        val tag = BackupJsonCodec.parseTags(fixture("tags.json")).single()
        assertEquals("原标签", tag.name); assertEquals("#1234AB", tag.color)
        assertEquals(3, tag.sortOrder)
        val achievement = BackupJsonCodec.parseAchievements(fixture("achievements.json")).single()
        assertEquals("milestone_1", achievement.achievementKey)
        assertEquals(1700000000000, achievement.unlockedAt)
        assertTrue(achievement.notified)
    }

    @Test fun oldSettingsPreserveExistingValuesAndDefaultOnlyNewFields() {
        val settings = BackupJsonCodec.parseSettings(fixture("settings.json"))
        assertEquals("旧机长", settings.username)
        assertEquals(2.5f, settings.mlPerSpurt, 0f)
        assertEquals(DefaultVolumeMode.SPURTS, settings.defaultVolumeMode)
        assertTrue(settings.dailyReminderEnabled)
        assertEquals("21:35", settings.dailyReminderTime)
        assertEquals("dark", settings.themeMode)
        assertEquals("{\"trend\":false}", settings.homeCardVisibility)
        assertEquals(80, settings.predictionMaxTicks)
        assertTrue(settings.liveUpdatesEnabled)
        assertFalse(settings.detailVideoHiddenByDefault)
        assertFalse(settings.onboardingCompleted) // Local completion is never imported.
    }

    @Test fun legacyImportCanBeExportedAndReadAgainWithoutChangingRecords() {
        val flights = BackupJsonCodec.parseFlights(fixture("flights.json"))
        assertEquals(flights, BackupJsonCodec.parseFlights(BackupJsonCodec.flightsToJson(flights).toString()))
        val lengths = BackupJsonCodec.parseLengths(fixture("length_records.json"))
        assertEquals(lengths, BackupJsonCodec.parseLengths(BackupJsonCodec.lengthsToJson(lengths).toString()))
    }

    @Test fun olderMissingModeRemainsReadableButCorruptedTypesAreRejected() {
        val array = JSONArray(fixture("flights.json"))
        array.getJSONObject(1).remove("volumeInputMode")
        assertEquals("milliliters", BackupJsonCodec.parseFlights(array.toString()).single { it.id == 2L }.volumeInputMode)
        array.getJSONObject(0).put("spurtCount", "3")
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.parseFlights(array.toString()) }
    }
}
