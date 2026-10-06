package com.risediary.app.data.backup

import com.risediary.app.data.DataMaintenanceGate
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The same filesystem inspection is used by Android AtomicFile storage at startup. */
class BackupRecoveryFileInspectionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun noCommittedCopyAndOnlyUncommittedNewFileDoesNotLockTheApp() {
        val base = File(temporary.root, "recovery.bin")
        File(base.path + ".new").writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(hasCommittedRecoveryFile(base))
        assertEquals(DataMaintenanceGate.State.IDLE, gateFor(base).state.value)
        assertTrue("Inspection does not destroy even an uncommitted copy", File(base.path + ".new").exists())
    }

    @Test fun committedBaseAlwaysRequiresRecoveryEvenWithAnIncompleteNewFile() {
        val base = File(temporary.root, "recovery.bin").apply { writeBytes(byteArrayOf(4, 5)) }
        File(base.path + ".new").writeBytes(byteArrayOf(1))
        assertTrue(hasCommittedRecoveryFile(base))
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gateFor(base).state.value)
    }

    @Test fun legacyAtomicBackupWithoutBaseStillRequiresRecovery() {
        val base = File(temporary.root, "recovery.bin")
        File(base.path + ".bak").writeBytes(byteArrayOf(4, 5))
        assertTrue(hasCommittedRecoveryFile(base))
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gateFor(base).state.value)
    }

    @Test fun missingMaintenanceDirectoryIsSafeWhenItsAncestorIsInspectable() {
        val base = File(temporary.root, "not-created/maintenance/recovery.bin")
        assertFalse(hasCommittedRecoveryFile(base))
        assertEquals(DataMaintenanceGate.State.IDLE, gateFor(base).state.value)
    }

    @Test fun fileInAnAncestorPathCannotBeMistakenForAnAbsentJournal() {
        val blocked = File(temporary.root, "blocked").apply { writeText("not a directory") }
        val base = File(blocked, "missing/maintenance/recovery.bin")
        assertNotNull(runCatching { hasCommittedRecoveryFile(base) }.exceptionOrNull())
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gateFor(base).state.value)
        assertEquals("not a directory", blocked.readText())
    }

    private fun gateFor(file: File): DataMaintenanceGate = DataMaintenanceGate(BackupRecoveryJournal(
        object : BackupRecoveryStorage {
            override fun exists() = hasCommittedRecoveryFile(file)
            override fun read(): ByteArray = throw IOException("Read is unnecessary for startup protection")
            override fun write(bytes: ByteArray) = error("Inspection must not write")
            override fun delete() = error("Inspection must not delete")
        }
    ))
}
