package com.risediary.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.entity.Flight
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupValidationTest {
    private lateinit var database: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = java.io.File(base.cacheDir, "backup-video-" + java.util.UUID.randomUUID())
        val context = object : android.content.ContextWrapper(base) {
            override fun getFilesDir(): java.io.File = files
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(
            context = context,
            database = database,
            preferences = UserPreferences(context),
            clock = Clock.systemUTC(),
            timerStore = com.risediary.app.service.TimerSessionStore(context, com.risediary.app.service.BootIdentityProvider { 1 },
                object : com.risediary.app.service.ElapsedRealtimeClock { override fun millis() = 1_000L }, Clock.systemUTC()),
            timerHolder = com.risediary.app.service.TimerStateHolder()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun missingRequiredEntryIsRejected() {
        val archive = zipOf(
            "flights.json" to "[]",
            "length_records.json" to "[]",
            "tags.json" to "[]",
            "achievements.json" to "[]"
        )

        assertThrows(IllegalArgumentException::class.java) {
            manager.readBackup(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun oversizedEntryIsRejectedBeforeParsing() {
        val archive = zipOf("flights.json" to "x".repeat(32 * 1024 * 1024 + 1))

        assertThrows(IllegalArgumentException::class.java) {
            manager.readBackup(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun oldBackupWithoutDefaultVolumeModeUsesMilliliters() {
        val archive = validArchive(
            flights = """
                [{
                  "id": 1,
                  "startTime": 1000,
                  "endTime": 2000,
                  "durationSeconds": 1,
                  "spurtCount": 3,
                  "semenVolumeMl": null,
                  "ejaculationDistanceCm": null,
                  "methodTags": "[]",
                  "moodNote": "",
                  "createdAt": 1000,
                  "updatedAt": 1000
                }]
            """.trimIndent(),
            settings = """
                {
                  "username": "机长",
                  "ml_per_spurt": 2.0,
                  "daily_reminder_enabled": false,
                  "daily_reminder_time": "22:00",
                  "inactive_reminder_enabled": false,
                  "inactive_reminder_days": 7,
                  "inactive_reminder_time": "22:00",
                  "monthly_length_reminder_enabled": false,
                  "monthly_length_reminder_day": 1,
                  "monthly_length_reminder_time": "22:00",
                  "reminder_sound": true,
                  "reminder_vibration": true,
                  "background_auto_lock_enabled": false,
                  "background_lock_mode": "always",
                  "theme_mode": "system",
                  "home_card_order": "[]",
                  "home_card_visibility": "{}",
                  "onboarding_completed": true
                }
            """.trimIndent()
        )

        val restored = manager.readBackup(ByteArrayInputStream(archive))

        assertEquals(80, restored.settings.predictionMaxTicks)
        assertEquals(3, restored.flights.single().legacySpurtCount)
        assertNull(restored.flights.single().legacyVolumeMl)
        assertEquals(DefaultVolumeMode.MILLILITERS, restored.settings.defaultVolumeMode)
        assertEquals(
            RecordVolumeMode.SPURTS.storedValue,
            restored.flights.single().volumeInputMode
        )
    }

    @Test
    fun backupCanRestoreSpurtDefaultVolumeMode() {
        val settings = """
            {
              "username": "机长",
              "ml_per_spurt": 2.0,
              "default_volume_mode": "spurts",
              "daily_reminder_enabled": false,
              "daily_reminder_time": "22:00",
              "inactive_reminder_enabled": false,
              "inactive_reminder_days": 7,
              "inactive_reminder_time": "22:00",
              "monthly_length_reminder_enabled": false,
              "monthly_length_reminder_day": 1,
              "monthly_length_reminder_time": "22:00",
              "reminder_sound": true,
              "reminder_vibration": true,
              "background_auto_lock_enabled": false,
              "background_lock_mode": "always",
              "theme_mode": "system",
              "home_card_order": "[]",
              "home_card_visibility": "{}",
              "onboarding_completed": true
            }
        """.trimIndent()

        val restored = manager.readBackup(ByteArrayInputStream(validArchive(settings)))

        assertEquals(DefaultVolumeMode.SPURTS, restored.settings.defaultVolumeMode)
    }

    @Test
    fun predictionRoundTripPreservesRecordedRangeAndExplicitNullHistory() {
        val original = Flight(id=1, startTime=1000, endTime=2000, durationSeconds=1,
            spurtCount=null, semenVolumeMl=2.3f, volumeInputMode="estimated", predictionMaxTicks=200,
            ejaculationDistanceCm=null, methodTags="[]", moodNote="", createdAt=1000, updatedAt=2000)
        val parsed = BackupJsonCodec.parseFlights(BackupJsonCodec.flightsToJson(listOf(original)).toString()).single()
        assertEquals(original, parsed)
        assertNull(parsed.legacyVolumeInputMode)
    }

    @Test
    fun modifiedLegacyQuantityRoundTripsWithoutOverwritingHistory() {
        val original = Flight(id=1, startTime=1000, endTime=2000, durationSeconds=1,
            spurtCount=null, semenVolumeMl=9.5f, volumeInputMode="milliliters",
            legacySpurtCount=3, legacyVolumeMl=6f, legacyVolumeInputMode="spurts",
            ejaculationDistanceCm=null, methodTags="[]", moodNote="", createdAt=1000, updatedAt=2000)
        assertEquals(original, BackupJsonCodec.parseFlights(BackupJsonCodec.flightsToJson(listOf(original)).toString()).single())
    }

    @Test
    fun unknownExplicitRecordModeAndFractionalPredictionRangeAreRejected() {
        val value = BackupJsonCodec.flightToJson(Flight(id=1, startTime=1000, endTime=2000, durationSeconds=1,
            spurtCount=null, semenVolumeMl=2.3f, volumeInputMode="estimated", predictionMaxTicks=80,
            ejaculationDistanceCm=null, methodTags="[]", moodNote="", createdAt=1000, updatedAt=2000))
        value.put("volumeInputMode", "unknown")
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.parseFlights(JSONArray().put(value).toString()) }
        value.put("volumeInputMode", "estimated").put("predictionMaxTicks", 80.5)
        assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.parseFlights(JSONArray().put(value).toString()) }
    }

    @Test
    fun videoAssociationRoundTripsEvenWhenOriginalFileDoesNotExist() {
        val original = videoFlight()
        val parsed = BackupJsonCodec.parseFlights(BackupJsonCodec.flightsToJson(listOf(original)).toString()).single()
        assertEquals(original, parsed)
        val old = BackupJsonCodec.flightToJson(original).apply {
            remove("videoUri"); remove("videoDisplayName"); remove("videoMimeType")
        }
        val legacy = BackupJsonCodec.parseFlights(JSONArray().put(old).toString()).single()
        assertNull(legacy.videoUri)
        assertNull(legacy.videoDisplayName)
        assertNull(legacy.videoMimeType)
    }

    @Test
    fun invalidOrPartialVideoMetadataIsRejectedWithoutDiscardingValidMissingFileReferences() {
        val source = BackupJsonCodec.flightToJson(videoFlight()).toString()
        val invalid = listOf(
            JSONObject(source).put("videoUri", "https://example.com/video.mp4"),
            JSONObject(source).put("videoUri", JSONObject.NULL),
            JSONObject(source).put("videoUri", 3),
            JSONObject(source).put("videoDisplayName", ""),
            JSONObject(source).put("videoDisplayName", JSONObject.NULL),
            JSONObject(source).put("videoMimeType", "image/jpeg")
        )
        invalid.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupJsonCodec.parseFlights(JSONArray().put(value).toString())
            }
        }
    }

    @Test
    fun exportedZipContainsOnlyJsonAndImportDoesNotAcquireVideoPermission() = kotlinx.coroutines.runBlocking {
        val original = videoFlight().copy(endTime = 13_000L, durationSeconds = 8,
            timingSource = "timer", recordDraftId = "video-session", recordSource = "wearable", sourceDeviceId = "band-app-1")
        database.flightDao().insert(original)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val before = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        val uri = com.risediary.app.media.TestVideoProvider.BACKUP
        org.junit.Assert.assertTrue(manager.exportToUri(uri) is BackupResult.Success)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val entries = mutableSetOf<String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name
                zip.closeEntry()
            }
        }
        assertEquals(setOf("flights.json", "length_records.json", "tags.json", "achievements.json", "settings.json"), entries)
        val restored = manager.readBackup(ByteArrayInputStream(bytes))
        assertEquals(original, restored.flights.single())
        org.junit.Assert.assertTrue(manager.clearAll() is BackupResult.Success)
        val result = manager.restoreFromUri(uri)
        org.junit.Assert.assertTrue(result is BackupResult.Success)
        org.junit.Assert.assertTrue((result as BackupResult.Success).message.contains("重新关联"))
        assertEquals(original, database.flightDao().getAll().single())
        assertEquals(before, context.contentResolver.persistedUriPermissions.map { it.uri }.toSet())
    }

    @Test
    fun timedRecordKeepsPauseSpanAndCommitIdentityInBackup() {
        val record = videoFlight().copy(endTime = 13_000L, durationSeconds = 8,
            timingSource = "timer", recordDraftId = "session")
        val json = BackupJsonCodec.flightsToJson(listOf(record)).toString()
        val settings = """{
            "username":"测试","ml_per_spurt":2.0,"daily_reminder_enabled":false,
            "daily_reminder_time":"22:00","inactive_reminder_enabled":false,"inactive_reminder_days":7,
            "monthly_length_reminder_enabled":false,"monthly_length_reminder_day":1,
            "reminder_sound":true,"reminder_vibration":true,"theme_mode":"system",
            "home_card_order":"[]","home_card_visibility":"{}","onboarding_completed":true
        }"""
        assertEquals(record, manager.readBackup(ByteArrayInputStream(validArchive(settings, json))).flights.single())
        val manual = BackupJsonCodec.flightsToJson(listOf(record.copy(timingSource = "manual"))).toString()
        assertThrows(IllegalArgumentException::class.java) { manager.readBackup(ByteArrayInputStream(validArchive(settings, manual))) }
        val unknown = BackupJsonCodec.flightsToJson(listOf(record.copy(timingSource = "unknown"))).toString()
        assertThrows(IllegalArgumentException::class.java) { manager.readBackup(ByteArrayInputStream(validArchive(settings, unknown))) }
    }

    @Test
    fun sameDeviceReadableVideoRestoreSucceedsWithoutRelinkNotice() = kotlinx.coroutines.runBlocking {
        val record = videoFlight().copy(videoUri = com.risediary.app.media.TestVideoProvider.READABLE.toString())
        database.flightDao().insert(record)
        val uri = com.risediary.app.media.TestVideoProvider.BACKUP
        org.junit.Assert.assertTrue(manager.exportToUri(uri) is BackupResult.Success)
        org.junit.Assert.assertTrue(manager.clearAll() is BackupResult.Success)
        val result = manager.restoreFromUri(uri)
        org.junit.Assert.assertTrue(result is BackupResult.Success)
        org.junit.Assert.assertFalse((result as BackupResult.Success).message.contains("重新关联"))
        assertEquals(record, database.flightDao().getAll().single())
    }


    @Test
    fun liveUpdateSettingRoundTripsAndOldBackupUsesEnabledDefault() {
        val legacy = """{
            "username":"测试","ml_per_spurt":2.0,"daily_reminder_enabled":false,
            "daily_reminder_time":"22:00","inactive_reminder_enabled":false,"inactive_reminder_days":7,
            "monthly_length_reminder_enabled":false,"monthly_length_reminder_day":1,
            "reminder_sound":true,"reminder_vibration":true,"theme_mode":"system",
            "home_card_order":"[]","home_card_visibility":"{}","onboarding_completed":true
        }"""
        org.junit.Assert.assertTrue(manager.readBackup(ByteArrayInputStream(validArchive(legacy))).settings.liveUpdatesEnabled)
        val settings = BackupJsonCodec.parseSettings(legacy)
        val encoded = BackupJsonCodec.settingsToJson(settings.copy(liveUpdatesEnabled = false))
        assertEquals(false, manager.readBackup(ByteArrayInputStream(validArchive(encoded.toString()))).settings.liveUpdatesEnabled)
        encoded.put("live_updates_enabled", 1)
        assertThrows(IllegalArgumentException::class.java) {
            manager.readBackup(ByteArrayInputStream(validArchive(encoded.toString())))
        }
    }

    @Test
    fun globalIdentityAndWearableOriginRoundTripWithoutChangingQuantityOrVideo() {
        val original = videoFlight().copy(recordSource = "wearable", sourceDeviceId = "band-app-1",
            legacySpurtCount = 3, legacyVolumeMl = 6f, legacyVolumeInputMode = "spurts")
        val json = BackupJsonCodec.flightsToJson(listOf(original)).toString()
        assertEquals(original, manager.readBackup(ByteArrayInputStream(validArchive(identitySettings(), json))).flights.single())
    }

    @Test
    fun legacyBackupWithNoIdentityGetsNewIdWhichNextBackupPreserves() {
        val original = videoFlight()
        val old = BackupJsonCodec.flightToJson(original).apply {
            remove("globalId"); remove("recordSource"); remove("sourceDeviceId")
        }
        val first = manager.readBackup(ByteArrayInputStream(validArchive(identitySettings(), JSONArray().put(old).toString()))).flights.single()
        com.risediary.app.data.sync.RecordIdentity.requireValidRecords(listOf(first))
        assertEquals(original.copy(globalId = first.globalId), first)
        val newBackup = BackupJsonCodec.flightsToJson(listOf(first)).toString()
        assertEquals(first, manager.readBackup(ByteArrayInputStream(validArchive(identitySettings(), newBackup))).flights.single())
    }

    @Test
    fun partialNullOrWrongTypeIdentityIsRejectedRatherThanSilentlyReplaced() {
        val encoded = BackupJsonCodec.flightToJson(videoFlight()).toString()
        val invalid = listOf(
            JSONObject(encoded).apply { remove("globalId") },
            JSONObject(encoded).apply { remove("recordSource") },
            JSONObject(encoded).apply { remove("sourceDeviceId") },
            JSONObject(encoded).put("globalId", JSONObject.NULL),
            JSONObject(encoded).put("recordSource", JSONObject.NULL),
            JSONObject(encoded).put("globalId", 123),
            JSONObject(encoded).put("recordSource", true),
            JSONObject(encoded).put("sourceDeviceId", JSONArray())
        )
        invalid.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                manager.readBackup(ByteArrayInputStream(validArchive(identitySettings(), JSONArray().put(value).toString())))
            }
        }
    }

    @Test
    fun invalidGlobalIdUnknownOriginAndInvalidDeviceIdAreRejected() {
        val encoded = BackupJsonCodec.flightToJson(videoFlight()).toString()
        val invalid = listOf(
            JSONObject(encoded).put("globalId", ""),
            JSONObject(encoded).put("globalId", "not-a-uuid"),
            JSONObject(encoded).put("globalId", "00000000-0000-0000-0000-000000000000"),
            JSONObject(encoded).put("globalId", "AE9675B5-6854-4DE6-85D7-28D2E899254A"),
            JSONObject(encoded).put("recordSource", "unknown"),
            JSONObject(encoded).put("sourceDeviceId", " "),
            JSONObject(encoded).put("sourceDeviceId", "x".repeat(129))
        )
        invalid.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                manager.readBackup(ByteArrayInputStream(validArchive(identitySettings(), JSONArray().put(value).toString())))
            }
        }
    }

    @Test
    fun duplicateGlobalIdentityRejectsRestoreBeforeExistingDataIsReplaced() = kotlinx.coroutines.runBlocking {
        val existing = videoFlight()
        database.flightDao().insert(existing)
        val duplicate = listOf(existing, existing.copy(id = 2L))
        val bytes = validArchive(identitySettings(), BackupJsonCodec.flightsToJson(duplicate).toString())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = com.risediary.app.media.TestVideoProvider.BACKUP
        context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
        org.junit.Assert.assertTrue(manager.restoreFromUri(uri) is BackupResult.Failure)
        assertEquals(listOf(existing), database.flightDao().getAll())
    }

    private fun identitySettings() = """{
        "username":"测试","ml_per_spurt":2.0,"daily_reminder_enabled":false,
        "daily_reminder_time":"22:00","inactive_reminder_enabled":false,"inactive_reminder_days":7,
        "monthly_length_reminder_enabled":false,"monthly_length_reminder_day":1,
        "reminder_sound":true,"reminder_vibration":true,"theme_mode":"system",
        "home_card_order":"[]","home_card_visibility":"{}","onboarding_completed":true
    }"""


    private fun videoFlight() = Flight(
        id=1, startTime=1000, endTime=61000, durationSeconds=60, spurtCount=null,
        semenVolumeMl=2.3f, volumeInputMode="estimated", predictionMaxTicks=80,
        ejaculationDistanceCm=null, methodTags="[]", moodNote="", createdAt=1000, updatedAt=61000,
        videoUri="content://com.example.documents/document/deleted-video",
        videoDisplayName="video.mp4", videoMimeType="video/mp4"
    )

    private fun validArchive(settings: String, flights: String = "[]"): ByteArray = zipOf(
        "flights.json" to flights,
        "length_records.json" to "[]",
        "tags.json" to "[]",
        "achievements.json" to "[]",
        "settings.json" to settings
    )

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
