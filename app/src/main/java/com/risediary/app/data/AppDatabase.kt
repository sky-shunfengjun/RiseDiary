package com.risediary.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.risediary.app.data.dao.AchievementDao
import com.risediary.app.data.dao.FlightDao
import com.risediary.app.data.dao.LengthRecordDao
import com.risediary.app.data.dao.TagDao
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.Tag

@Database(
    entities = [Flight::class, LengthRecord::class, Tag::class, Achievement::class, com.risediary.app.data.draft.RecordDraftEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordDraftDao(): com.risediary.app.data.draft.RecordDraftDao
    abstract fun flightDao(): FlightDao
    abstract fun lengthRecordDao(): LengthRecordDao
    abstract fun tagDao(): TagDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flights ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE flights ADD COLUMN recordSource TEXT NOT NULL DEFAULT 'phone'")
                db.execSQL("ALTER TABLE flights ADD COLUMN sourceDeviceId TEXT DEFAULT NULL")
                db.query("SELECT id FROM flights ORDER BY id").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "UPDATE flights SET globalId = ? WHERE id = ?",
                            arrayOf<Any>(java.util.UUID.randomUUID().toString(), cursor.getLong(0))
                        )
                    }
                }
                db.execSQL("CREATE UNIQUE INDEX index_flights_globalId ON flights(globalId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flights ADD COLUMN recordDraftId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE flights ADD COLUMN timingSource TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("CREATE UNIQUE INDEX index_flights_recordDraftId ON flights(recordDraftId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS record_drafts (draftId TEXT NOT NULL PRIMARY KEY, activeSlot INTEGER, revision INTEGER NOT NULL, payload TEXT NOT NULL, completedFlightId INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX index_record_drafts_activeSlot ON record_drafts(activeSlot)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM achievements WHERE achievementKey LIKE 'first_of_month_%'"
                )
                db.execSQL(
                    """
                    DELETE FROM tags
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM tags GROUP BY name
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TEMP TABLE achievements_merged AS
                    SELECT
                        MIN(id) AS id,
                        achievementKey,
                        MIN(unlockedAt) AS unlockedAt,
                        MAX(notified) AS notified
                    FROM achievements
                    GROUP BY achievementKey
                    """.trimIndent()
                )
                db.execSQL("DELETE FROM achievements")
                db.execSQL(
                    """
                    INSERT INTO achievements(id, achievementKey, unlockedAt, notified)
                    SELECT id, achievementKey, unlockedAt, notified
                    FROM achievements_merged
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE achievements_merged")

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_flights_startTime ON flights(startTime)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_length_records_recordDate ON length_records(recordDate)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_achievements_achievementKey ON achievements(achievementKey)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE flights ADD COLUMN volumeInputMode TEXT NOT NULL " +
                        "DEFAULT 'milliliters'"
                )
                db.execSQL(
                    """
                    UPDATE flights
                    SET volumeInputMode = 'spurts'
                    WHERE spurtCount IS NOT NULL AND semenVolumeMl IS NULL
                    """.trimIndent()
                )
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flights ADD COLUMN videoUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE flights ADD COLUMN videoDisplayName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE flights ADD COLUMN videoMimeType TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flights ADD COLUMN legacySpurtCount INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE flights ADD COLUMN legacyVolumeMl REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE flights ADD COLUMN legacyVolumeInputMode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE flights ADD COLUMN predictionMaxTicks INTEGER DEFAULT NULL")
                db.execSQL("UPDATE flights SET legacySpurtCount = spurtCount, legacyVolumeMl = semenVolumeMl, legacyVolumeInputMode = volumeInputMode")
            }
        }
    }
}
