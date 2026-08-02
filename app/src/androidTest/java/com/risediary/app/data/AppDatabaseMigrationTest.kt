package com.risediary.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private lateinit var context: Context
    private val databaseName = "migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFromOneToTwoPreservesDataAndDeduplicatesUniqueValues() {
        createVersionOneDatabase().use { db ->
            db.execSQL(
                """
                INSERT INTO tags(id, name, color, sortOrder)
                VALUES (1, '手动', '#FF9800', 0), (2, '手动', '#000000', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO achievements(id, achievementKey, unlockedAt, notified)
                VALUES (1, 'milestone_1', 200, 0), (2, 'milestone_1', 100, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO flights(
                    id, startTime, endTime, durationSeconds, spurtCount,
                    semenVolumeMl, ejaculationDistanceCm, methodTags, moodNote,
                    createdAt, updatedAt
                ) VALUES (1, 1000, 2000, 1, 1, 2.0, NULL, '[]', '', 1000, 1000)
                """.trimIndent()
            )
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        val sqlite = database.openHelper.writableDatabase

        assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM flights"))
        assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM tags"))
        assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM achievements"))
        sqlite.query(
            "SELECT unlockedAt, notified FROM achievements WHERE achievementKey = 'milestone_1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(100L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
        }
        assertTrue(hasIndex(sqlite, "index_flights_startTime"))
        assertTrue(hasIndex(sqlite, "index_tags_name"))
        assertEquals(
            "milliliters",
            queryString(sqlite, "SELECT volumeInputMode FROM flights WHERE id = 1")
        )
        database.close()
    }

    @Test
    fun migrationFromTwoToThreeInfersLegacyInputModeWithoutLosingValues() {
        createVersionTwoDatabase().use { db ->
            db.execSQL(
                """
                INSERT INTO flights(
                    id, startTime, endTime, durationSeconds, spurtCount,
                    semenVolumeMl, ejaculationDistanceCm, methodTags, moodNote,
                    createdAt, updatedAt
                ) VALUES
                    (1, 1000, 2000, 1, 2, NULL, NULL, '[]', '', 1000, 1000),
                    (2, 3000, 4000, 1, 3, 6.0, NULL, '[]', '', 3000, 3000)
                """.trimIndent()
            )
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        val sqlite = database.openHelper.writableDatabase

        assertEquals(
            "spurts",
            queryString(sqlite, "SELECT volumeInputMode FROM flights WHERE id = 1")
        )
        assertEquals(
            "milliliters",
            queryString(sqlite, "SELECT volumeInputMode FROM flights WHERE id = 2")
        )
        assertEquals(3, queryCount(sqlite, "SELECT spurtCount FROM flights WHERE id = 2"))
        database.close()
    }

    private fun createVersionOneDatabase(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE flights (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            startTime INTEGER NOT NULL,
                            endTime INTEGER NOT NULL,
                            durationSeconds INTEGER NOT NULL,
                            spurtCount INTEGER,
                            semenVolumeMl REAL,
                            ejaculationDistanceCm REAL,
                            methodTags TEXT NOT NULL,
                            moodNote TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE length_records (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            recordDate INTEGER NOT NULL,
                            flaccidLengthCm REAL NOT NULL,
                            erectLengthCm REAL NOT NULL,
                            note TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE tags (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            color TEXT NOT NULL,
                            sortOrder INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE achievements (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            achievementKey TEXT NOT NULL,
                            unlockedAt INTEGER NOT NULL,
                            notified INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun createVersionTwoDatabase(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createVersionOneDatabaseSchema(db)
                    db.execSQL("CREATE INDEX index_flights_startTime ON flights(startTime)")
                    db.execSQL(
                        "CREATE INDEX index_length_records_recordDate ON length_records(recordDate)"
                    )
                    db.execSQL("CREATE UNIQUE INDEX index_tags_name ON tags(name)")
                    db.execSQL(
                        "CREATE UNIQUE INDEX index_achievements_achievementKey " +
                            "ON achievements(achievementKey)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun createVersionOneDatabaseSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE flights (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                spurtCount INTEGER,
                semenVolumeMl REAL,
                ejaculationDistanceCm REAL,
                methodTags TEXT NOT NULL,
                moodNote TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE length_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recordDate INTEGER NOT NULL,
                flaccidLengthCm REAL NOT NULL,
                erectLengthCm REAL NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE achievements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                achievementKey TEXT NOT NULL,
                unlockedAt INTEGER NOT NULL,
                notified INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun queryCount(database: SupportSQLiteDatabase, sql: String): Int =
        database.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun hasIndex(database: SupportSQLiteDatabase, name: String): Boolean =
        database.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(name)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) == 1
        }

    private fun queryString(database: SupportSQLiteDatabase, sql: String): String =
        database.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
}
