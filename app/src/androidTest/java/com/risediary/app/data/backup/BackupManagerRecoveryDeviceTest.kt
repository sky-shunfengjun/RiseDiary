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
            f.assertOriginalTables()
            assertEquals(501, f.database.flightDao().getAll().single().spurtCount)
            assertEquals(1002f, f.database.flightDao().getAll().single().semenVolumeMl!!, 0f)
            assertEquals(10_001, f.database.flightDao().getAll().single().moodNote.length)
            assertEquals(20f, f.preferences.mlPerSpurt.first(), 0f)
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
        val manager = BackupManager(context, database, preferences,
            Clock.fixed(Instant.ofEpochMilli(1_700_000_100_000L), ZoneOffset.UTC),
            com.risediary.app.service.TimerSessionStore(context, com.risediary.app.service.BootIdentityProvider { 1 },
                object : com.risediary.app.service.ElapsedRealtimeClock { override fun millis() = 1_000L },
                Clock.fixed(Instant.ofEpochMilli(1_700_000_100_000L), ZoneOffset.UTC)),
            com.risediary.app.service.TimerStateHolder())

        suspend fun assertOriginalTables() {
            assertEquals(listOf(original), database.flightDao().getAll())
            assertEquals(listOf(tag), database.tagDao().getAll())
            assertEquals(listOf(length), database.lengthRecordDao().getAll())
            assertEquals(listOf(achievement), database.achievementDao().getAll())
        }
        fun close() { database.close(); scope.cancel() }

        companion object {
            suspend fun create(): Fixture {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val file = File(context.cacheDir, "prefs-review-${UUID.randomUUID()}/settings.preferences_pb")
                val disk = PreferenceDataStoreFactory.create(scope = scope,
                    produceFile = { file.apply { parentFile?.mkdirs() } })
                val storage = FaultableStore(disk)
                val gate = DataMaintenanceGate()
                val preferences = UserPreferences(storage, gate)
                val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
                val fixture = Fixture(context, database, preferences, storage, gate, scope)
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
