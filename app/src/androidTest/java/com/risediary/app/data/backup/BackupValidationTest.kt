package com.risediary.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.RecordVolumeMode
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(
            context = context,
            database = database,
            preferences = UserPreferences(context),
            clock = Clock.systemUTC()
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
        val archive = zipOf("flights.json" to "x".repeat(5 * 1024 * 1024 + 1))

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
