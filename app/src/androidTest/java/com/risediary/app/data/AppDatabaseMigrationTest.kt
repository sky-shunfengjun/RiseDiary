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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
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
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
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

    @Test
    fun migrationFromThreeToFourArchivesValuesWithoutInventingMissingUnits() {
        createVersionTwoDatabase(version = 3).use { db ->
            db.execSQL("""
                INSERT INTO flights(id, startTime, endTime, durationSeconds, spurtCount,
                    semenVolumeMl, volumeInputMode, ejaculationDistanceCm, methodTags, moodNote, createdAt, updatedAt)
                VALUES (1,1000,2000,1,3,6.0,'spurts',NULL,'[]','both',1000,2000),
                       (2,3000,4000,1,4,NULL,'spurts',NULL,'[]','spurts',3000,4000),
                       (3,5000,6000,1,NULL,1.55,'milliliters',NULL,'[]','ml',5000,6000)
            """.trimIndent())
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(8, sqlite.version)
            assertEquals(3, queryCount(sqlite, "SELECT COUNT(*) FROM flights WHERE " +
                "legacySpurtCount IS spurtCount AND legacyVolumeMl IS semenVolumeMl AND legacyVolumeInputMode = volumeInputMode"))
            assertEquals(3, queryCount(sqlite, "SELECT COUNT(*) FROM flights WHERE predictionMaxTicks IS NULL"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM flights WHERE id=2 AND semenVolumeMl IS NULL AND legacyVolumeMl IS NULL"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM flights WHERE id=3 AND spurtCount IS NULL AND legacySpurtCount IS NULL"))
        } finally { database.close() }
    }

    @Test
    fun migrationFromFourToFiveAddsEmptyVideoWithoutChangingExistingRecordAndOtherTables() {
        createVersionTwoDatabase(version = 4).use { db ->
            db.execSQL("""
                INSERT INTO flights(id,startTime,endTime,durationSeconds,spurtCount,semenVolumeMl,
                    volumeInputMode,methodTags,moodNote,createdAt,updatedAt,legacySpurtCount,
                    legacyVolumeMl,legacyVolumeInputMode,predictionMaxTicks)
                VALUES (1,1000,61000,60,NULL,2.3,'estimated','["手动"]','keep',1000,61000,
                    3,6.0,'spurts',80)
            """.trimIndent())
            db.execSQL("INSERT INTO length_records VALUES(1,1000,8.0,12.0,'length')")
            db.execSQL("INSERT INTO tags VALUES(1,'手动','#FF9800',0)")
            db.execSQL("INSERT INTO achievements VALUES(1,'milestone_1',1000,1)")
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(8, sqlite.version)
            assertEquals(1, queryCount(sqlite, """SELECT COUNT(*) FROM flights WHERE id=1 AND
                startTime=1000 AND endTime=61000 AND durationSeconds=60 AND semenVolumeMl=2.3 AND
                volumeInputMode='estimated' AND legacySpurtCount=3 AND legacyVolumeMl=6.0 AND
                predictionMaxTicks=80 AND videoUri IS NULL AND videoDisplayName IS NULL AND videoMimeType IS NULL"""))
            assertEquals("keep", queryString(sqlite, "SELECT moodNote FROM flights WHERE id=1"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM length_records"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM tags"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM achievements"))
        } finally { database.close() }
    }

    @Test
    fun migrationFromFivePreservesVideoAndAddsManualSourceAndUniquePendingDraftSlot() {
        createVersionTwoDatabase(version = 5).use { db ->
            db.execSQL("""INSERT INTO flights(id,startTime,endTime,durationSeconds,spurtCount,semenVolumeMl,
                volumeInputMode,methodTags,moodNote,createdAt,updatedAt,videoUri,videoDisplayName,videoMimeType)
                VALUES (7,1000,9000,8,NULL,2.3,'milliliters','[]','keep',1000,9000,
                'content://videos/document/1','video.mp4','video/mp4')""")
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(8, sqlite.version)
            assertEquals("manual", queryString(sqlite, "SELECT timingSource FROM flights WHERE id=7"))
            assertEquals("content://videos/document/1", queryString(sqlite, "SELECT videoUri FROM flights WHERE id=7"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM flights WHERE recordDraftId IS NULL"))
            assertEquals(0, queryCount(sqlite, "SELECT COUNT(*) FROM record_drafts"))
            sqlite.execSQL("INSERT INTO record_drafts VALUES('first',1,1,'{}',NULL)")
            assertTrue(runCatching { sqlite.execSQL("INSERT INTO record_drafts VALUES('second',1,1,'{}',NULL)") }.isFailure)
        } finally { database.close() }
    }

    @Test
    fun migrationFromSixAssignsStableDistinctIdentityWithoutChangingRecordData() = kotlinx.coroutines.runBlocking {
        createVersionTwoDatabase(version = 6).use { db ->
            db.execSQL("""INSERT INTO flights(id,startTime,endTime,durationSeconds,spurtCount,semenVolumeMl,
                volumeInputMode,methodTags,moodNote,createdAt,updatedAt,legacySpurtCount,legacyVolumeMl,
                legacyVolumeInputMode,predictionMaxTicks,videoUri,videoDisplayName,videoMimeType,recordDraftId,timingSource)
                VALUES (7,1000,13000,8,NULL,2.3,'estimated','["手动"]','keep',1000,13000,3,6.0,
                'spurts',80,'content://videos/document/1','video.mp4','video/mp4','saved-session','timer'),
                (9,20000,80000,60,4,NULL,'spurts','[]','old',20000,80000,4,NULL,'spurts',NULL,
                NULL,NULL,NULL,NULL,'manual')""")
            db.execSQL("INSERT INTO length_records VALUES(1,1000,8.0,12.0,'length')")
            db.execSQL("INSERT INTO tags VALUES(1,'手动','#FF9800',0)")
            db.execSQL("INSERT INTO achievements VALUES(1,'milestone_1',1000,1)")
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8).allowMainThreadQueries().build()
        val records = try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(8, sqlite.version)
            assertTrue(hasIndex(sqlite, "index_flights_globalId"))
            assertEquals(1, queryCount(sqlite, """SELECT COUNT(*) FROM flights WHERE id=7 AND
                startTime=1000 AND endTime=13000 AND durationSeconds=8 AND spurtCount IS NULL AND
                semenVolumeMl=2.3 AND volumeInputMode='estimated' AND legacySpurtCount=3 AND legacyVolumeMl=6.0 AND
                legacyVolumeInputMode='spurts' AND predictionMaxTicks=80 AND
                videoUri='content://videos/document/1' AND videoDisplayName='video.mp4' AND videoMimeType='video/mp4' AND
                recordDraftId='saved-session' AND timingSource='timer' AND moodNote='keep' AND
                createdAt=1000 AND updatedAt=13000"""))
            assertEquals(1, queryCount(sqlite, """SELECT COUNT(*) FROM flights WHERE id=9 AND
                spurtCount=4 AND semenVolumeMl IS NULL AND legacySpurtCount=4 AND legacyVolumeMl IS NULL AND
                legacyVolumeInputMode='spurts' AND videoUri IS NULL AND recordDraftId IS NULL"""))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM length_records"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM tags"))
            assertEquals(1, queryCount(sqlite, "SELECT COUNT(*) FROM achievements"))
            val migrated = database.flightDao().getAll()
            assertEquals(2, migrated.size)
            com.risediary.app.data.sync.RecordIdentity.requireValidRecords(migrated)
            assertTrue(migrated.all { it.recordSource == "phone" && it.sourceDeviceId == null })
            org.junit.Assert.assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                kotlinx.coroutines.runBlocking {
                    database.flightDao().insertNew(migrated.first().copy(id = 0, recordDraftId = "duplicate-identity"))
                }
            }
            assertEquals(2, database.flightDao().getAll().size)
            migrated
        } finally { database.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries().build()
        try { assertEquals(records, reopened.flightDao().getAll()) }
        finally { reopened.close() }
    }


    @Test fun migrationFromSevenAssignsLengthIdsAcrossBatchesAndKeepsThemOnEditAndReopen() = kotlinx.coroutines.runBlocking {
        createVersionTwoDatabase(version=7).use { sqlite ->
            repeat(501) { index -> sqlite.execSQL("INSERT INTO length_records VALUES(?,1000,8.0,12.0,'keep')",arrayOf<Any>(index+1)) }
        }
        val database = Room.databaseBuilder(context,AppDatabase::class.java,databaseName)
            .addMigrations(AppDatabase.MIGRATION_7_8).build()
        val originals = try {
            val rows=database.lengthRecordDao().getAll()
            assertEquals(501,rows.size); assertEquals(501,rows.map { it.globalId }.distinct().size)
            assertTrue(rows.all { com.risediary.app.data.sync.RecordIdentity.isValidId(it.globalId) })
            val edited=rows.first().copy(note="edited")
            database.lengthRecordDao().update(edited)
            assertEquals(edited.globalId,database.lengthRecordDao().getById(edited.id)!!.globalId)
            database.lengthRecordDao().getAll()
        } finally { database.close() }
        val reopened=Room.databaseBuilder(context,AppDatabase::class.java,databaseName).build()
        try { assertEquals(originals,reopened.lengthRecordDao().getAll()) }
        finally { reopened.close() }
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

    private fun createVersionTwoDatabase(version: Int = 2): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createVersionOneDatabaseSchema(db)
                    if (version >= 3) AppDatabase.MIGRATION_2_3.migrate(db)
                    if (version >= 4) AppDatabase.MIGRATION_3_4.migrate(db)
                    if (version >= 5) AppDatabase.MIGRATION_4_5.migrate(db)
                    if (version >= 6) AppDatabase.MIGRATION_5_6.migrate(db)
                    if (version >= 7) AppDatabase.MIGRATION_6_7.migrate(db)
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
