package com.risediary.app.data.backup

import androidx.datastore.preferences.core.*
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.draft.RecordDraftEntity
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.Tag
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRecoveryJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun reconstructedJournalRetainsEveryRawPreferenceTypeAndOriginalRows() {
        val file = File(temporary.root, "recovery.bin")
        val original = snapshot()
        BackupRecoveryJournal(Disk(file)).persist(original)

        assertTrue("The original data must be on disk before replacement", file.isFile)
        val restored = requireNotNull(BackupRecoveryJournal(Disk(file)).load())
        assertEquals(original.flights, restored.flights)
        assertEquals(original.lengthRecords, restored.lengthRecords)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.achievements, restored.achievements)
        assertEquals(original.drafts, restored.drafts)
        assertEquals(original.timer, restored.timer)
        assertEquals(true, restored.preferences[booleanPreferencesKey("flag")])
        assertEquals(1.25f, restored.preferences[floatPreferencesKey("float")])
        assertEquals(1.23456789012345, restored.preferences[doublePreferencesKey("double")])
        assertEquals(-7, restored.preferences[intPreferencesKey("int")])
        assertEquals(Long.MAX_VALUE, restored.preferences[longPreferencesKey("long")])
        assertEquals("test-only-secret", restored.preferences[stringPreferencesKey("app_lock_pin")])
        assertEquals(setOf("a", "中文"), restored.preferences[stringSetPreferencesKey("set")])
        assertArrayEquals(byteArrayOf(0, 1, -1), restored.preferences[byteArrayPreferencesKey("bytes")])
        // A rollback journal preserves imperfect historical rows; it is not a user import.
        assertEquals(100L, restored.flights.single().updatedAt)
        assertEquals(200L, restored.flights.single().createdAt)
    }

    @Test fun processRecreationStartsReadonlyAndDoesNotAdmitWrites() = runTest {
        val file = File(temporary.root, "recovery.bin")
        BackupRecoveryJournal(Disk(file)).persist(snapshot())

        val recreated = DataMaintenanceGate(BackupRecoveryJournal(Disk(file)))
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, recreated.state.value)
        var touched = false
        assertTrue(runCatching { recreated.write { touched = true } }.exceptionOrNull() is DataMaintenanceBusyException)
        assertFalse(touched)
    }

    @Test fun writeFailurePropagatesBeforeAnyDestructiveWorkCanBegin() {
        val storage = Disk(File(temporary.root, "recovery.bin")).apply { failWrite = true }
        var destructiveWork = false
        val failure = runCatching {
            BackupRecoveryJournal(storage).persist(snapshot())
            destructiveWork = true
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(destructiveWork)
        assertFalse(storage.file.exists())
    }

    @Test fun damagedDiskJournalKeepsReadonlyProtectionAndItsOriginalBytes() {
        val file = File(temporary.root, "recovery.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val journal = BackupRecoveryJournal(Disk(file))
        val gate = DataMaintenanceGate(journal)
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gate.state.value)
        assertNotNull(runCatching { journal.load() }.exceptionOrNull())
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertTrue(journal.pending())
    }

    @Test fun inabilityToInspectTheJournalClosesWritesInsteadOfAssumingNoRecovery() {
        val storage = Disk(File(temporary.root, "recovery.bin")).apply { failInspect = true }
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED,
            DataMaintenanceGate(BackupRecoveryJournal(storage)).state.value)
    }

    @Test fun successfulRecoveryMayClearTheJournalAndFreshInstanceOpensWrites() = runTest {
        val file = File(temporary.root, "recovery.bin")
        val journal = BackupRecoveryJournal(Disk(file))
        journal.persist(snapshot())
        assertTrue(journal.pending())
        journal.clear()
        assertFalse(file.exists())
        assertFalse(journal.pending())
        val fresh = DataMaintenanceGate(BackupRecoveryJournal(Disk(file)))
        assertEquals(DataMaintenanceGate.State.IDLE, fresh.state.value)
        fresh.write { }
    }

    @Test fun failedRemovalDoesNotDiscardTheOnlyRecoveryCopy() {
        val storage = Disk(File(temporary.root, "recovery.bin"))
        val journal = BackupRecoveryJournal(storage)
        journal.persist(snapshot())
        storage.failDelete = true
        assertTrue(runCatching { journal.clear() }.exceptionOrNull() is IOException)
        assertTrue(journal.pending())
        assertNotNull(journal.load())
    }

    @Test fun corruptPayloadChecksumIsRejectedWithoutRemovingRecovery() {
        val file = File(temporary.root, "recovery.bin")
        BackupRecoveryJournal(Disk(file)).persist(snapshot())
        assertTrue("Checksum validation requires a durable journal", file.isFile)
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        val reconstructed = BackupRecoveryJournal(Disk(file))
        assertNotNull(runCatching { reconstructed.load() }.exceptionOrNull())
        assertTrue(reconstructed.pending())
        assertTrue(file.isFile)
    }

    @Test fun storageThatSilentlyCommitsIncompleteBytesCannotStartDestructiveWork() {
        val disk = Disk(File(temporary.root, "recovery.bin"))
        val incomplete = object : BackupRecoveryStorage by disk {
            override fun writeStream(block: (java.io.OutputStream) -> Unit) {
                val output = java.io.ByteArrayOutputStream()
                block(output)
                val bytes = output.toByteArray()
                disk.write(bytes.copyOf(bytes.size - 1))
            }
        }
        var destructiveWork = false
        val journal = BackupRecoveryJournal(incomplete)
        assertTrue(runCatching {
            journal.persist(snapshot())
            destructiveWork = true
        }.exceptionOrNull() is IOException)
        assertFalse(destructiveWork)
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, DataMaintenanceGate(journal).state.value)
        assertTrue(disk.file.isFile)
    }

    @Test fun anotherReplacementCannotOverwriteAnUnresolvedOriginalCopy() {
        val disk = Disk(File(temporary.root, "recovery.bin"))
        val journal = BackupRecoveryJournal(disk)
        journal.persist(snapshot())
        val originalBytes = disk.file.readBytes()
        assertNotNull(runCatching { journal.persist(snapshot().copy(flights = emptyList())) }.exceptionOrNull())
        assertArrayEquals(originalBytes, disk.file.readBytes())
        assertEquals(snapshot().flights, requireNotNull(journal.load()).flights)
    }

    private fun snapshot(): BackupRecoverySnapshot {
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey("flag") to true,
            floatPreferencesKey("float") to 1.25f,
            doublePreferencesKey("double") to 1.23456789012345,
            intPreferencesKey("int") to -7,
            longPreferencesKey("long") to Long.MAX_VALUE,
            stringPreferencesKey("app_lock_pin") to "test-only-secret",
            stringSetPreferencesKey("set") to setOf("a", "中文"),
            byteArrayPreferencesKey("bytes") to byteArrayOf(0, 1, -1)
        )
        return BackupRecoverySnapshot(
            flights = listOf(Flight(id = 7, startTime = 1_000, endTime = 61_000,
                durationSeconds = 60, spurtCount = 5, semenVolumeMl = 12.345f,
                ejaculationDistanceCm = 3f, methodTags = "[\"旧标签\"]", moodNote = "旧备注",
                createdAt = 200, updatedAt = 100, legacySpurtCount = 5, legacyVolumeMl = 12.345f,
                legacyVolumeInputMode = "spurts", videoUri = "content://test/document/7",
                videoDisplayName = "片段.mp4", videoMimeType = "video/mp4", recordDraftId = "submission-7",
                globalId = "12345678-1234-1234-1234-123456789012", recordSource = "wearable", sourceDeviceId = "device-test")),
            lengthRecords = listOf(LengthRecord(8, 5_000, 9.25f, 12.75f, "旧长度")),
            tags = listOf(Tag(9, "旧标签", "#123456", 4)),
            achievements = listOf(Achievement(10, "milestone_1", 8_000, true)),
            preferences = preferences.toPreferences(),
            drafts = listOf(RecordDraftEntity("old-draft", null, 4, "raw payload", 7)),
            timer = TimerSession(status = TimerStatus.FINISHED, startedAtEpochMillis = 1_000,
                elapsedMillis = 60_000, sessionId = "timer-test", endedAtEpochMillis = 61_000,
                bootCount = 3, liveUpdateDismissed = true)
        )
    }

    private class Disk(val file: File) : BackupRecoveryStorage {
        var failWrite = false
        var failDelete = false
        var failInspect = false
        override fun exists(): Boolean {
            if (failInspect) throw IOException("inspection failure")
            return file.exists()
        }
        override fun read(): ByteArray = file.readBytes()
        override fun write(bytes: ByteArray) {
            if (failWrite) throw IOException("write failure")
            file.outputStream().use { it.write(bytes); it.fd.sync() }
        }
        override fun delete() {
            if (failDelete) throw IOException("delete failure")
            if (file.exists() && !file.delete()) throw IOException("delete failure")
        }
    }
}
