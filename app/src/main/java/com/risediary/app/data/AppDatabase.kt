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
    entities = [Flight::class, LengthRecord::class, Tag::class, Achievement::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun flightDao(): FlightDao
    abstract fun lengthRecordDao(): LengthRecordDao
    abstract fun tagDao(): TagDao
    abstract fun achievementDao(): AchievementDao

    companion object {
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
    }
}
