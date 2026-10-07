package com.risediary.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.*
import com.risediary.app.data.entity.*
import com.risediary.app.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs against real Room, streaming ZIP and private staging; compile does not mean device-pass. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreWorkflowDeviceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var manager: BackupManager
    private lateinit var timerHolder: TimerStateHolder
    private lateinit var directory: File
    private var prepared: String? = null
    private val a = "12345678-1234-1234-1234-123456789012"
    private val b = "87654321-4321-4321-4321-210987654321"
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        directory=File(context.cacheDir,"dev41-test-"+UUID.randomUUID()).apply { mkdirs() }
        val gate=DataMaintenanceGate(BackupRecoveryJournal(AndroidBackupRecoveryStorage(File(directory,"recovery.bin"))))
        preferences=UserPreferences(Memory(),gate)
        database=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val clock=Clock.systemUTC()
        val timer=TimerSessionStore(Memory(),BootIdentityProvider { 1 },object : ElapsedRealtimeClock { override fun millis()=1000L },clock)
        timerHolder=TimerStateHolder()
        manager=BackupManager(context,database,preferences,clock,timer,timerHolder)
    }
    @After fun teardown() = runBlocking {
        prepared?.let { manager.discardRestore(it) }
        database.close(); directory.deleteRecursively(); Unit
    }
    private fun flight(id: Long,global: String=a,note: String="local")=Flight(id,1000,61000,60,null,2.3f,
        "milliliters",null,"[\"旧方式\"]",note,1000,5000,globalId=global)
    private fun archive(flights: List<JSONObject> = emptyList(), lengths: List<JSONObject> = emptyList(),
        tags: List<Tag> = emptyList(), settings: JSONObject? = BackupJsonCodec.settingsToJson(defaultBackupSettings())): Uri {
        val file=File(directory,UUID.randomUUID().toString()+".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String,value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
            entry("flights.json",flights.joinToString(",","[","]"))
            entry("length_records.json",lengths.joinToString(",","[","]"))
            entry("tags.json",tags.map(BackupJsonCodec::tagToJson).joinToString(",","[","]"))
            entry("achievements.json","[]")
            settings?.let { entry("settings.json",it.toString()) }
        }
        return Uri.fromFile(file)
    }
    private suspend fun prepare(uri: Uri, mode: RestoreMode=RestoreMode.MERGE)=manager.prepareRestore(uri,mode).also { prepared=it.preparationId }
    private suspend fun restore(uri: Uri,mode: RestoreMode=RestoreMode.MERGE): BackupResult {
        val preview=prepare(uri,mode)
        return (manager.confirmRestore(preview.preparationId,preview.revision) as RestoreConfirmation.Finished).result
    }
    private fun legacy(row: Flight)=BackupJsonCodec.flightToJson(row).apply {
        remove("globalId"); remove("recordSource"); remove("sourceDeviceId")
    }
    @Test fun mergeUsesBackupEvenWhenOlderAndPreservesPhoneOnlyRowsAndSubmitKey() = runBlocking {
        val local=flight(7).copy(recordDraftId="submit")
        database.flightDao().insertNew(local); database.flightDao().insertNew(flight(8,b,"phone-only"))
        val backup=local.copy(id=99,moodNote="older backup",updatedAt=2000)
        val uri=archive(listOf(BackupJsonCodec.flightToJson(backup)))
        val preview=prepare(uri)
        assertEquals(1,preview.flights.updated); assertEquals(2,preview.flights.final)
        assertTrue((manager.confirmRestore(preview.preparationId,preview.revision) as RestoreConfirmation.Finished).result is BackupResult.Success)
        assertEquals("older backup",database.flightDao().getById(7)!!.moodNote)
        assertEquals("submit",database.flightDao().getById(7)!!.recordDraftId)
        assertEquals(2,database.flightDao().getAll().size)
        assertTrue(restore(uri) is BackupResult.Success)
        assertEquals(2,database.flightDao().getAll().size)
    }
    @Test fun numericIdCollisionIsNotAStableMatch() = runBlocking {
        database.flightDao().insertNew(flight(7))
        val preview=prepare(archive(listOf(BackupJsonCodec.flightToJson(flight(7,b,"foreign")))))
        assertEquals(1,preview.flights.added); assertEquals(0,preview.flights.updated)
        manager.confirmRestore(preview.preparationId,preview.revision)
        assertEquals(setOf(7L,8L),database.flightDao().getAll().map { it.id }.toSet())
    }
    @Test fun legacyMultipleLocalCandidatesKeepsDuplicatesAndLastBackupNumericIdWins() = runBlocking {
        database.flightDao().insertNew(flight(3,a,"first")); database.flightDao().insertNew(flight(4,b,"second"))
        val uri=archive(listOf(legacy(flight(9,note="last")),legacy(flight(1,note="earlier"))))
        assertTrue(restore(uri) is BackupResult.Success)
        assertEquals("last",database.flightDao().getById(3)!!.moodNote)
        assertEquals("second",database.flightDao().getById(4)!!.moodNote)
        assertTrue(restore(uri) is BackupResult.Success); assertEquals(2,database.flightDao().getAll().size)
    }
    @Test fun replaceRetainsDistinctLegacyBackupRowsAndReplacesCatalogs() = runBlocking {
        database.flightDao().insertNew(flight(8)); database.tagDao().insert(Tag(1,"手机标签","#123456"))
        val preview=prepare(archive(listOf(legacy(flight(1)),legacy(flight(2))),tags=listOf(Tag(2,"备份标签","#000000"))),RestoreMode.REPLACE)
        assertEquals(1,preview.flights.current); assertEquals(2,preview.flights.backup)
        manager.confirmRestore(preview.preparationId,preview.revision)
        assertEquals(2,database.flightDao().getAll().size)
        assertEquals(listOf("备份标签"),database.tagDao().getAll().map { it.name })
        assertEquals("[\"旧方式\"]",database.flightDao().getAll().first().methodTags)
    }
    @Test fun missingSettingsResetsOrdinaryDefaultsButKeepsLocalSecurityAndCompletion() = runBlocking {
        preferences.setUsername("phone"); preferences.setAppLock(true,"1234"); preferences.setOnboardingCompleted(true)
        assertTrue(restore(archive(settings=null)) is BackupResult.Success)
        assertEquals("机长",preferences.settingsSnapshot(preferences.rawSnapshot()).username)
        assertTrue(preferences.securitySettings.first().onboardingCompleted)
        assertEquals("1234",preferences.securitySettings.first().credential)
    }
    @Test fun nullAndEmptySettingsDefaultButIllegalKnownFieldRejectsBeforeChanges() = runBlocking {
        val settings=JSONObject().put("username",JSONObject.NULL).put("daily_reminder_time","")
            .put("live_updates_enabled",false).put("prediction_max_ticks",120)
        assertTrue(restore(archive(settings=settings)) is BackupResult.Success)
        assertEquals("22:00",preferences.settingsSnapshot(preferences.rawSnapshot()).dailyReminderTime)
        assertEquals(120,preferences.settingsSnapshot(preferences.rawSnapshot()).predictionMaxTicks)
        val before=preferences.rawSnapshot()
        assertTrue(runCatching { prepare(archive(settings=JSONObject().put("live_updates_enabled","false"))) }.isFailure)
        assertEquals(before,preferences.rawSnapshot())
    }
    @Test fun previewChangeRequiresAnotherConfirmationAndRejectsOldRevision() = runBlocking {
        val first=prepare(archive(listOf(BackupJsonCodec.flightToJson(flight(1)))))
        database.flightDao().insertNew(flight(7,b,"new phone"))
        val changed=manager.confirmRestore(first.preparationId,first.revision) as RestoreConfirmation.Changed
        assertEquals(2,changed.preview.flights.final)
        assertEquals(listOf("new phone"),database.flightDao().getAll().map { it.moodNote })
        assertTrue(manager.confirmRestore(first.preparationId,first.revision) is RestoreConfirmation.Changed)
        assertTrue((manager.confirmRestore(first.preparationId,changed.preview.revision) as RestoreConfirmation.Finished).result is BackupResult.Success)
        assertTrue(runCatching { manager.confirmRestore(first.preparationId,changed.preview.revision) }.isFailure)
    }
    @Test fun modeSwitchReusesFileAndCancelCannotBeConfirmed() = runBlocking {
        database.flightDao().insertNew(flight(7))
        val original=prepare(archive(listOf(BackupJsonCodec.flightToJson(flight(1,b)))))
        val replacement=manager.changeRestoreMode(original.preparationId,RestoreMode.REPLACE)
        assertEquals(original.preparationId,replacement.preparationId)
        assertTrue(replacement.revision>original.revision); assertEquals(1,replacement.flights.final)
        manager.discardRestore(replacement.preparationId)
        assertTrue(runCatching { manager.confirmRestore(replacement.preparationId,replacement.revision) }.isFailure)
        assertEquals(1,database.flightDao().getAll().size)
    }
    @Test fun legacyLengthTimestampMatchesSmallestLocalIdAndNewIdentitySurvivesEdits() = runBlocking {
        val local=LengthRecord(4,1000,8f,12f,"phone")
        database.lengthRecordDao().insertNew(local)
        val row=BackupJsonCodec.lengthToJson(local.copy(id=99,note="backup")).apply { remove("globalId") }
        assertTrue(restore(archive(lengths=listOf(row))) is BackupResult.Success)
        assertEquals(local.globalId,database.lengthRecordDao().getById(4)!!.globalId)
        assertEquals("backup",database.lengthRecordDao().getById(4)!!.note)
    }
    @Test fun duplicateSubmissionConflictRejectsWholePreview() = runBlocking {
        database.flightDao().insertNew(flight(7).copy(recordDraftId="shared"))
        val foreign=flight(1,b).copy(recordDraftId="shared")
        assertTrue(runCatching { prepare(archive(listOf(BackupJsonCodec.flightToJson(foreign)))) }.isFailure)
        assertEquals(1,database.flightDao().getAll().size)
    }
    @Test fun moreThanOldFiveMiBExportsRestoresAndExportsAgain() = runBlocking {
        val note="large " .repeat(1000)
        repeat(1000) { index -> database.flightDao().insertNew(flight(index+1L,UUID.randomUUID().toString(),note)) }
        val file=File(directory,"large.zip")
        assertTrue(manager.exportToUri(Uri.fromFile(file)) is BackupResult.Success)
        java.util.zip.ZipFile(file).use { assertTrue(it.getEntry("flights.json").size>5L*1024*1024) }
        assertTrue(restore(Uri.fromFile(file)) is BackupResult.Success)
        assertEquals(1000,database.flightDao().getAll().size)
        assertTrue(manager.exportToUri(Uri.fromFile(File(directory,"again.zip"))) is BackupResult.Success)
    }
    @Test fun modifiedStagingFileCannotWriteIntoFormalDatabase() = runBlocking {
        database.flightDao().insertNew(flight(7))
        val current=prepare(archive(listOf(BackupJsonCodec.flightToJson(flight(1,b)))))
        val staged=File(context.cacheDir,"restore-previews/${current.preparationId}/result.db")
        android.database.sqlite.SQLiteDatabase.openDatabase(staged.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE rows SET payload='{}' WHERE kind='flights.json'")
        }
        assertTrue(runCatching { manager.confirmRestore(current.preparationId,current.revision) }.isFailure)
        assertEquals(listOf(7L),database.flightDao().getAll().map { it.id })
    }

    @Test fun overflowingRecordAndSettingIntegersRejectBeforeFormalChanges() = runBlocking {
        database.flightDao().insertNew(flight(7))
        val bad=BackupJsonCodec.flightToJson(flight(1,b)).put("durationSeconds",4_294_967_356L)
        assertTrue(runCatching { prepare(archive(listOf(bad))) }.isFailure)
        assertTrue(runCatching { prepare(archive(settings=JSONObject().put("monthly_length_reminder_day",4_294_967_297L))) }.isFailure)
        assertEquals(listOf(7L),database.flightDao().getAll().map { it.id })
    }
    @Test fun malformedUtf8RejectsRatherThanReplacingStoredRecordText() = runBlocking {
        database.flightDao().insertNew(flight(7))
        val file=File(directory,"malformed.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            for (name in BackupFiles.arrays) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(if(name==BackupFiles.FLIGHTS) byteArrayOf(0x5b,0xc3.toByte(),0x28,0x5d) else "[]".toByteArray())
                zip.closeEntry()
            }
        }
        assertTrue(runCatching { prepare(Uri.fromFile(file)) }.isFailure)
        assertEquals(listOf(7L),database.flightDao().getAll().map { it.id })
    }

    @Test fun legacyDuplicatesThatFinallyRestoreOriginalAreSkippedInPreview() = runBlocking {
        val original=flight(7,note="original")
        database.flightDao().insertNew(original)
        val preview=prepare(archive(listOf(legacy(original.copy(id=1,moodNote="intermediate")),legacy(original.copy(id=2)))))
        assertEquals(0,preview.flights.updated); assertEquals(1,preview.flights.skipped)
        assertEquals(1,preview.flights.final); assertEquals(2,preview.flights.backup)
        assertTrue((manager.confirmRestore(preview.preparationId,preview.revision) as RestoreConfirmation.Finished).result is BackupResult.Success)
        assertEquals(original,database.flightDao().getById(7))
    }

    private fun v1Archive(pretty: Boolean = true): Uri {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val file = File(directory, "v1-${UUID.randomUUID()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            BackupFiles.all.forEach { name ->
                val original = assets.open("backup-v1/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = if (pretty) original else if (name == BackupFiles.SETTINGS)
                    JSONObject(original).toString() else org.json.JSONArray(original).toString()
                zip.putNextEntry(ZipEntry(name)); zip.write(json.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    @Test fun actualV1FormatMergesWithoutLosingPhoneRecordsAndRepeatedRestoreDoesNotDuplicate() = runBlocking {
        val phone = flight(2, b, "phone-only")
        database.flightDao().insertNew(phone)
        val uri = v1Archive()
        val first = prepare(uri)
        assertEquals(3, first.flights.added); assertEquals(4, first.flights.final)
        assertEquals(phone, database.flightDao().getById(2)) // Preview is read-only.
        assertTrue((manager.confirmRestore(first.preparationId, first.revision) as RestoreConfirmation.Finished).result is BackupResult.Success)
        val old = database.flightDao().getAll().single { it.startTime == 1700000000000 }
        assertEquals(3, old.spurtCount); assertEquals(5.25f, old.semenVolumeMl!!, 0f)
        assertEquals(3, old.legacySpurtCount); assertEquals(5.25f, old.legacyVolumeMl!!, 0f)
        assertEquals("旧版备注\n第二行 😀", old.moodNote)
        assertEquals(phone, database.flightDao().getById(2))
        assertTrue(restore(uri) is BackupResult.Success)
        assertEquals(4, database.flightDao().getAll().size)
        assertEquals(1, database.lengthRecordDao().getAll().size)
        assertEquals(old.globalId, database.flightDao().getAll().single { it.startTime == old.startTime }.globalId)
        assertEquals("原标签", database.tagDao().getAll().single().name)
        assertTrue(database.achievementDao().getAll().single().notified)
        assertEquals("旧机长", preferences.username.first())
        assertEquals(80, preferences.predictionMaxTicks.first())
        assertTrue(preferences.liveUpdatesEnabled.first())
        val exported = File(directory, "old-import-reexport.zip")
        assertTrue(manager.exportToUri(Uri.fromFile(exported)) is BackupResult.Success)
        assertTrue(restore(Uri.fromFile(exported)) is BackupResult.Success)
        assertEquals(4, database.flightDao().getAll().size)
    }

    @Test fun compactV112FormatSupportsReplaceAndPreservesLocalSecurityAndGuideState() = runBlocking {
        database.flightDao().insertNew(flight(2, b))
        preferences.restoreRaw(preferences.rawSnapshot().toMutablePreferences().apply {
            this[booleanPreferencesKey("onboarding_completed")] = true
            this[stringPreferencesKey("last_completed_update_intro_id")] = com.risediary.app.data.UpdateIntroCampaign.ID
            this[booleanPreferencesKey("app_lock_enabled")] = true
            this[stringPreferencesKey("app_lock_pin")] = "local credential"
        })
        val original = preferences.rawSnapshot()
        assertTrue(restore(v1Archive(false), RestoreMode.REPLACE) is BackupResult.Success)
        val rows = database.flightDao().getAll()
        assertEquals(3, rows.size)
        assertFalse(rows.any { it.globalId == b })
        assertEquals(120, rows.single { it.startTime == 1700000000000 }.durationSeconds)
        assertEquals(original[booleanPreferencesKey("app_lock_enabled")], preferences.rawSnapshot()[booleanPreferencesKey("app_lock_enabled")])
        assertEquals(original[stringPreferencesKey("app_lock_pin")], preferences.rawSnapshot()[stringPreferencesKey("app_lock_pin")])
        assertEquals(original[booleanPreferencesKey("onboarding_completed")], preferences.rawSnapshot()[booleanPreferencesKey("onboarding_completed")])
        assertEquals(original[stringPreferencesKey("last_completed_update_intro_id")], preferences.rawSnapshot()[stringPreferencesKey("last_completed_update_intro_id")])
    }

    @Test fun moreThan100TagsSurviveMergeReplaceAndReExport() = runBlocking {
        val tags = (1..151).map { "方式$it" }
        val row = flight(1).copy(methodTags = com.risediary.app.data.repository.TagJson.encode(tags))
        val uri = archive(flights = listOf(BackupJsonCodec.flightToJson(row)))
        for (mode in listOf(RestoreMode.MERGE, RestoreMode.REPLACE)) {
            assertTrue(restore(uri, mode) is BackupResult.Success)
            assertEquals(tags, com.risediary.app.data.repository.TagJson.decode(database.flightDao().getAll().single().methodTags))
            val output = File(directory, "tags-${mode.name}.zip")
            assertTrue(manager.exportToUri(Uri.fromFile(output)) is BackupResult.Success)
            assertTrue(restore(Uri.fromFile(output), mode) is BackupResult.Success)
            assertEquals(tags, com.risediary.app.data.repository.TagJson.decode(database.flightDao().getAll().single().methodTags))
        }
    }

    @Test fun failedTimerPersistenceBlocksPreviewWithoutChangingRecords() = runBlocking {
        database.flightDao().insertNew(flight(7))
        timerHolder.setPersistenceError(true)
        assertTrue(runCatching { prepare(archive()) }.isFailure)
        assertEquals(listOf(7L), database.flightDao().getAll().map { it.id })
        timerHolder.setPersistenceError(false)
        assertEquals(1, prepare(archive()).flights.final)
    }

    @Test fun timerFailureAfterPreviewBlocksConfirmationAndCanRetryTheSamePreview() = runBlocking {
        database.flightDao().insertNew(flight(7))
        val preview = prepare(archive(listOf(BackupJsonCodec.flightToJson(flight(1,b,"backup")))))
        timerHolder.setPersistenceError(true)
        assertTrue(runCatching { manager.confirmRestore(preview.preparationId,preview.revision) }.isFailure)
        assertEquals(listOf(7L), database.flightDao().getAll().map { it.id })
        assertEquals(DataMaintenanceGate.State.IDLE, manager.maintenanceState.value)
        timerHolder.setPersistenceError(false)
        assertTrue((manager.confirmRestore(preview.preparationId,preview.revision) as RestoreConfirmation.Finished).result is BackupResult.Success)
        assertEquals(2, database.flightDao().getAll().size)
    }

    @Test fun failedTimerPersistenceBlocksClearWithoutLosingRecords() = runBlocking {
        database.flightDao().insertNew(flight(7))
        timerHolder.setPersistenceError(true)
        assertTrue(manager.clearAll() is BackupResult.Failure)
        assertEquals(listOf(7L), database.flightDao().getAll().map { it.id })
        timerHolder.setPersistenceError(false)
        assertTrue(manager.clearAll() is BackupResult.Success)
        assertTrue(database.flightDao().getAll().isEmpty())
    }

    private class Memory : DataStore<Preferences> {
        override val data=MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences)->Preferences): Preferences = transform(data.value).also { data.value=it }
    }
}
