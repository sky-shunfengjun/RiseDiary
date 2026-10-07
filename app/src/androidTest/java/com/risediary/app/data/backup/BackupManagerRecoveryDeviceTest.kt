package com.risediary.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.entity.Tag
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real Room and DataStore; the storage decorator controls only the write failure boundary. */
class BackupManagerRecoveryDeviceTest {
    @Test fun settingsFailureAfterDatabaseReplacementRestoresTablesAndRawPreferences() = runBlocking {
        val f = Fixture.create()
        try {
            val raw = f.preferences.rawSnapshot()
            f.storage.failOn(1)
            val result = f.manager.clearAll()
            assertTrue(result is BackupResult.Failure)
            assertTrue((result as BackupResult.Failure).message.contains("已还原"))
            f.assertOriginalTables()
            assertEquals(raw, f.preferences.rawSnapshot())
            assertEquals(DataMaintenanceGate.State.IDLE, f.gate.state.value)
        } finally { f.close() }
    }

    @Test fun lockClearFailureRestoresAlreadyChangedSettingsRuntimeMarksAndPin() = runBlocking {
        val f = Fixture.create()
        try {
            val raw = f.preferences.rawSnapshot()
            f.storage.failOn(2)
            val result = f.manager.clearAll()
            assertTrue(result is BackupResult.Failure)
            f.assertOriginalTables()
            assertEquals(raw, f.preferences.rawSnapshot())
            assertTrue(f.preferences.securitySettings.first().lockEnabled)
            assertEquals("1234", f.preferences.securitySettings.first().credential)
            assertEquals(DataMaintenanceGate.State.IDLE, f.gate.state.value)
        } finally { f.close() }
    }

    @Test fun rollbackFailureKeepsReadonlyAndRetryCompletesOriginalRecovery() = runBlocking {
        val f = Fixture.create()
        try {
            val raw = f.preferences.rawSnapshot()
            f.storage.failOn(1, 2)
            val result = f.manager.clearAll()
            assertTrue(result is BackupResult.Failure)
            assertTrue((result as BackupResult.Failure).message.contains("尚未完整还原"))
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, f.gate.state.value)
            assertTrue(runCatching { f.preferences.setUsername("禁止迟到写入") }.exceptionOrNull() is DataMaintenanceBusyException)
            f.storage.failOn()
            assertTrue(f.manager.retryRecovery() is BackupResult.Success)
            f.assertOriginalTables()
            assertEquals(raw, f.preferences.rawSnapshot())
            assertEquals(DataMaintenanceGate.State.IDLE, f.gate.state.value)
        } finally { f.close() }
    }

    @Test fun cancellingPageOwnerAfterDatabaseCommitDoesNotInterruptRemainingCriticalWrites() = runBlocking {
        val f = Fixture.create()
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var pageOwner: kotlinx.coroutines.Job? = null
        try {
            f.storage.blockNextWrite(reached, release)
            pageOwner = launch { f.manager.clearAll() }
            reached.await()
            assertTrue(f.database.flightDao().getAll().isEmpty())
            assertEquals(DataMaintenanceGate.State.WORKING, f.gate.state.value)
            pageOwner.cancel()
            assertTrue(runCatching { f.preferences.setUsername("维护中不写入") }.exceptionOrNull() is DataMaintenanceBusyException)
            release.complete(Unit)
            pageOwner.join()
            assertTrue(f.database.flightDao().getAll().isEmpty())
            assertFalse(f.preferences.securitySettings.first().lockEnabled)
            assertEquals("机长", f.preferences.username.first())
            assertEquals(DataMaintenanceGate.State.IDLE, f.gate.state.value)
        } finally {
            release.complete(Unit)
            pageOwner?.join()
            f.close()
        }
    }
    @Test fun actualZipRoundTripPreservesDerivedQuantityAndHistoricalLongNote() = runBlocking {
        val f = Fixture.create()
        try {
            val zip = File(f.context.cacheDir, "backup-review-${UUID.randomUUID()}.zip")
            val uri = Uri.fromFile(zip)
            assertTrue(f.manager.exportToUri(uri) is BackupResult.Success)
            assertTrue(f.manager.clearAll() is BackupResult.Success)
            assertTrue(f.manager.restoreFromUri(uri) is BackupResult.Success)
            val restoredFlight = f.database.flightDao().getAll().single()
            assertEquals(f.original.copy(id=restoredFlight.id),restoredFlight)
            assertEquals(501, f.database.flightDao().getAll().single().spurtCount)
            assertEquals(1002f, f.database.flightDao().getAll().single().semenVolumeMl!!, 0f)
            assertEquals(10_001, f.database.flightDao().getAll().single().moodNote.length)
            assertEquals(20f, f.preferences.mlPerSpurt.first(), 0f)
        } finally { f.close() }
    }

    @Test fun restoredVideoCheckFailureDoesNotRollbackCommittedData() = runBlocking {
        val f = Fixture.create()
        try {
            val record = f.original.copy(videoUri = "content://com.example.documents/document/missing",
                videoDisplayName = "missing.mp4", videoMimeType = "video/mp4")
            f.database.flightDao().update(record)
            val uri = Uri.fromFile(File(f.context.cacheDir, "video-backup-${UUID.randomUUID()}.zip"))
            assertTrue(f.manager.exportToUri(uri) is BackupResult.Success)
            assertTrue(f.manager.clearAll() is BackupResult.Success)
            val result = f.manager.restoreFromUri(uri)
            assertTrue(result is BackupResult.Success)
            assertTrue((result as BackupResult.Success).message.contains("重新关联"))
            assertEquals(record.copy(id=f.database.flightDao().getAll().single().id), f.database.flightDao().getAll().single())
            assertEquals(DataMaintenanceGate.State.IDLE, f.gate.state.value)
        } finally { f.close() }
    }

    @Test fun failedClearRestoresPendingDraftPreimageAsWellAsSavedTables() = runBlocking {
        val f = Fixture.create()
        try {
            val snapshot = com.risediary.app.data.draft.RecordDraftSnapshot("pending", revision = 1L,
                startTime = 1_700_000_000_000L, endTime = 1_700_000_060_000L, durationSeconds = 60,
                quantity = com.risediary.app.ui.form.QuantityDraftSnapshot("estimated", 23, "9.5", 80, true))
            val row = com.risediary.app.data.draft.RecordDraftEntity("pending", 1, 1,
                com.risediary.app.data.draft.RecordDraftCodec.encode(snapshot), null)
            f.database.recordDraftDao().insert(row)
            f.storage.failOn(1)
            assertTrue(f.manager.clearAll() is BackupResult.Failure)
            f.assertOriginalTables()
            assertEquals(listOf(row), f.database.recordDraftDao().getAll())
        } finally { f.close() }
    }

    @Test fun journalWriteFailureCannotReplaceOriginalTablesOrPreferences() = runBlocking {
        val f = Fixture.create()
        try {
            val raw = f.preferences.rawSnapshot()
            f.recoveryDisk.failWrite = true
            assertTrue(f.manager.clearAll() is BackupResult.Failure)
            f.assertOriginalTables()
            assertEquals(raw, f.preferences.rawSnapshot())
            assertFalse(f.recoveryDisk.exists())
            assertEquals(DataMaintenanceGate.State.IDLE, f.gate.state.value)
        } finally { f.close() }
    }

    @Test fun reconstructedManagerRecoversOriginalRowsFromDiskAfterRollbackFailure() = runBlocking {
        val f = Fixture.create()
        try {
            val raw = f.preferences.rawSnapshot()
            val draft = com.risediary.app.data.draft.RecordDraftEntity("historical", null, 4, "raw payload", 7)
            f.database.recordDraftDao().insert(draft)
            f.preventOriginalFlightReinsert()
            f.storage.failOn(1)
            assertTrue(f.manager.clearAll() is BackupResult.Failure)
            assertTrue("The failed rollback really leaves the original record missing", f.database.flightDao().getAll().isEmpty())
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, f.gate.state.value)
            assertTrue(f.recoveryDisk.file.isFile)

            // No original-row memory from BackupManager or the old gate is available to this instance.
            val recreated = f.recreate()
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, recreated.gate.state.value)
            assertTrue(runCatching { recreated.preferences.setUsername("cannot write") }.exceptionOrNull() is DataMaintenanceBusyException)
            f.allowOriginalFlightReinsert()
            f.storage.failOn()
            assertTrue(recreated.manager.retryRecovery() is BackupResult.Success)
            f.assertOriginalTables()
            assertEquals(listOf(draft), f.database.recordDraftDao().getAll())
            assertEquals(raw, recreated.preferences.rawSnapshot())
            assertFalse(f.recoveryDisk.exists())
            assertEquals(DataMaintenanceGate.State.IDLE, recreated.gate.state.value)
        } finally { f.close() }
    }

    @Test fun damagedRecoveryAfterReconstructionCannotReopenWritesOrDiscardTheCopy() = runBlocking {
        val f = Fixture.create()
        try {
            f.preventOriginalFlightReinsert()
            f.storage.failOn(1)
            assertTrue(f.manager.clearAll() is BackupResult.Failure)
            val damaged = byteArrayOf(1, 2, 3)
            f.recoveryDisk.file.writeBytes(damaged)
            val recreated = f.recreate()
            f.allowOriginalFlightReinsert()
            f.storage.failOn()
            assertTrue(recreated.manager.retryRecovery() is BackupResult.Failure)
            assertArrayEquals(damaged, f.recoveryDisk.file.readBytes())
            assertTrue(f.database.flightDao().getAll().isEmpty())
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, recreated.gate.state.value)
            assertTrue(runCatching { recreated.preferences.setUsername("cannot write") }.exceptionOrNull() is DataMaintenanceBusyException)
        } finally { f.close() }
    }

    @Test fun exportNormalizesImperfectOldUpdateTimeWithoutChangingStoredRows() = runBlocking {
        val f = Fixture.create()
        val zip = File(f.context.cacheDir, "old-time-review-${UUID.randomUUID()}.zip")
        try {
            val imperfect = f.original.copy(updatedAt = f.original.createdAt - 1_000L)
            f.database.flightDao().update(imperfect)
            assertTrue(f.manager.exportToUri(Uri.fromFile(zip)) is BackupResult.Success)
            val exported = zip.inputStream().use(f.manager::readBackup)
            assertEquals(imperfect.createdAt, exported.flights.single().updatedAt)
            assertEquals(imperfect, f.database.flightDao().getAll().single())
        } finally { zip.delete(); f.close() }
    }

    @Test fun onlyUncommittedAtomicTempFileDoesNotTrapStartupInReadonly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = File(context.cacheDir, "orphan-recovery-${UUID.randomUUID()}.bin")
        val staging = File(base.path + ".new")
        try {
            staging.writeBytes(byteArrayOf(1, 2, 3))
            val disk = AndroidBackupRecoveryStorage(base)
            val gate = DataMaintenanceGate(BackupRecoveryJournal(disk))
            assertEquals(DataMaintenanceGate.State.IDLE, gate.state.value)
            gate.write { }
            assertTrue(staging.exists())
        } finally { staging.delete(); base.delete() }
    }

    @Test fun committedLegacyAtomicBackupStillProtectsAndRecoversOriginalRows() = runBlocking {
        val f = Fixture.create()
        val legacyBackup = File(f.recoveryDisk.file.path + ".bak")
        try {
            f.preventOriginalFlightReinsert()
            f.storage.failOn(1)
            assertTrue(f.manager.clearAll() is BackupResult.Failure)
            assertTrue(f.recoveryDisk.file.renameTo(legacyBackup))
            val recreated = f.recreate()
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, recreated.gate.state.value)
            f.allowOriginalFlightReinsert()
            f.storage.failOn()
            assertTrue(recreated.manager.retryRecovery() is BackupResult.Success)
            f.assertOriginalTables()
            assertFalse(f.recoveryDisk.exists())
        } finally { f.close() }
    }

    @Test fun uninspectableNestedParentFailsClosedInsteadOfAssumingNoRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blocked = File(context.cacheDir, "blocked-recovery-${UUID.randomUUID()}")
        try {
            blocked.writeText("not a directory")
            val disk = AndroidBackupRecoveryStorage(File(blocked, "missing/maintenance/recovery.bin"))
            val gate = DataMaintenanceGate(BackupRecoveryJournal(disk))
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gate.state.value)
            assertEquals("not a directory", blocked.readText())
        } finally { blocked.delete() }
    }

    private class RecoveryDisk(val file: File) : BackupRecoveryStorage {
        private val delegate = AndroidBackupRecoveryStorage(file)
        var failWrite = false
        override fun exists() = delegate.exists()
        override fun read() = delegate.read()
        override fun write(bytes: ByteArray) {
            if (failWrite) throw IOException("Controlled journal write failure")
            delegate.write(bytes)
        }
        override fun openRead() = delegate.openRead()
        override fun writeStream(block: (java.io.OutputStream) -> Unit) {
            if (failWrite) throw IOException("Controlled journal write failure")
            delegate.writeStream(block)
        }
        override fun delete() = delegate.delete()
    }

    private data class Recreated(val gate: DataMaintenanceGate, val preferences: UserPreferences, val manager: BackupManager)

    private class FaultableStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> = delegate.data
        private var writes = 0
        private var failures = emptySet<Int>()
        private var barrier: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        fun failOn(vararg counts: Int) { writes = 0; failures = counts.toSet(); barrier = null }
        fun blockNextWrite(reached: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>) {
            failOn(); barrier = reached to release
        }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            barrier?.let { (reached, release) ->
                barrier = null
                reached.complete(Unit)
                release.await()
            }
            if (writes in failures) throw IOException("Controlled storage failure on write $writes")
            return delegate.updateData(transform)
        }
    }

    private class Fixture private constructor(
        val context: Context,
        val database: AppDatabase,
        val preferences: UserPreferences,
        val storage: FaultableStore,
        val gate: DataMaintenanceGate,
        val recoveryDisk: RecoveryDisk,
        private val scope: CoroutineScope
    ) {
        val original = Flight(id = 7L, startTime = 1_700_000_000_000L, endTime = 1_700_000_060_000L,
            durationSeconds = 60, spurtCount = 501, semenVolumeMl = 1002f,
            volumeInputMode = RecordVolumeMode.SPURTS.storedValue, ejaculationDistanceCm = 4f,
            methodTags = "[\"原标签\"]", moodNote = "旧".repeat(10_001),
            createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L)
        private val tag = Tag(3L, "原标签", "#123456", 0)
        private val length = LengthRecord(4L, 1_700_000_000_000L, 10f, 15f, "旧长度")
        private val achievement = Achievement(5L, "milestone_1", 1_700_000_000_000L, true)
        private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_100_000L), ZoneOffset.UTC)
        private val timerFile = File(context.cacheDir, "timer-review-${UUID.randomUUID()}/session.preferences_pb")
        private val timerDataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = {
            timerFile.apply { parentFile?.mkdirs() }
        })
        private fun timerStore() = com.risediary.app.service.TimerSessionStore(timerDataStore,
            com.risediary.app.service.BootIdentityProvider { 1 },
                object : com.risediary.app.service.ElapsedRealtimeClock { override fun millis() = 1_000L },
                clock)
        val manager = BackupManager(context, database, preferences, clock, timerStore(),
            com.risediary.app.service.TimerStateHolder())

        fun recreate(): Recreated {
            val gate = DataMaintenanceGate(BackupRecoveryJournal(RecoveryDisk(recoveryDisk.file)))
            val preferences = UserPreferences(storage, gate)
            return Recreated(gate, preferences, BackupManager(context, database, preferences, clock,
                timerStore(), com.risediary.app.service.TimerStateHolder()))
        }
        fun preventOriginalFlightReinsert() {
            database.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER reject_original BEFORE INSERT ON flights WHEN NEW.id = 7
                BEGIN SELECT RAISE(ABORT, 'Controlled original row restore failure'); END
            """.trimIndent())
        }
        fun allowOriginalFlightReinsert() {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_original")
        }
        suspend fun assertOriginalTables() {
            assertEquals(listOf(original), database.flightDao().getAll())
            assertEquals(listOf(tag), database.tagDao().getAll())
            assertEquals(listOf(length), database.lengthRecordDao().getAll())
            assertEquals(listOf(achievement), database.achievementDao().getAll())
        }
        fun close() { database.close(); scope.cancel(); recoveryDisk.delete() }

        companion object {
            suspend fun create(): Fixture {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val file = File(context.cacheDir, "prefs-review-${UUID.randomUUID()}/settings.preferences_pb")
                val disk = PreferenceDataStoreFactory.create(scope = scope,
                    produceFile = { file.apply { parentFile?.mkdirs() } })
                val storage = FaultableStore(disk)
                val recoveryDisk = RecoveryDisk(File(context.cacheDir, "recovery-review-${UUID.randomUUID()}.bin"))
                val gate = DataMaintenanceGate(BackupRecoveryJournal(recoveryDisk))
                val preferences = UserPreferences(storage, gate)
                val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
                val fixture = Fixture(context, database, preferences, storage, gate, recoveryDisk, scope)
                database.flightDao().insert(fixture.original)
                database.tagDao().insert(fixture.tag)
                database.lengthRecordDao().insert(fixture.length)
                database.achievementDao().insert(fixture.achievement)
                preferences.setUsername("原机长")
                preferences.setMlPerSpurt(20f)
                preferences.setAppLock(true, "1234")
                preferences.setAppLockFailureState(3, 2_000L)
                preferences.markDailyReminderSent(123L)
                preferences.markInactiveReminderSent(124L)
                preferences.markMonthlyReminderSent("2026-09")
                return fixture
            }
        }
    }
}
