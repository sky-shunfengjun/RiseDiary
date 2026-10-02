package com.risediary.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DataMaintenanceGateRaceTest {
    @Test fun maintenanceIncludesInFlightWriteAndRejectsOldQueuedWrite() = runTest {
        val gate = DataMaintenanceGate()
        val firstWrite = CompletableDeferred<Unit>()
        val maintenanceEnd = CompletableDeferred<Unit>()
        val entries = mutableListOf<String>()
        val first = launch { gate.write { entries.add("write-start"); firstWrite.await(); entries.add("write-end") } }
        runCurrent()
        val queued = async { runCatching { gate.write { entries.add("queued-write") } }.exceptionOrNull() }
        runCurrent()
        val operation = launch { gate.maintenance { entries.add("snapshot"); maintenanceEnd.await() } }
        runCurrent()
        assertEquals(DataMaintenanceGate.State.WORKING, gate.state.value)
        assertEquals(listOf("write-start"), entries)
        firstWrite.complete(Unit); first.join(); runCurrent()
        assertTrue(queued.await() is DataMaintenanceBusyException)
        assertEquals(listOf("write-start", "write-end", "snapshot"), entries)
        maintenanceEnd.complete(Unit); operation.join()
        assertEquals(DataMaintenanceGate.State.IDLE, gate.state.value)
        gate.write { entries.add("fresh-write") }
        assertEquals("fresh-write", entries.last())
    }

    @Test fun cancellationAfterReplaceDoesNotInterruptRequiredRollback() = runTest {
        val gate = DataMaintenanceGate()
        val writeFailure = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val operation = launch {
            gate.maintenance {
                events.add("replaced")
                try { writeFailure.await(); error("settings failed") }
                catch (_: IllegalStateException) { events.add("rolled-back") }
            }
        }
        runCurrent(); operation.cancel(); runCurrent()
        assertEquals(DataMaintenanceGate.State.WORKING, gate.state.value)
        assertEquals(listOf("replaced"), events)
        writeFailure.complete(Unit); operation.join()
        assertEquals(listOf("replaced", "rolled-back"), events)
        assertEquals(DataMaintenanceGate.State.IDLE, gate.state.value)
    }

    @Test fun failedRollbackKeepsWritesClosedUntilRecoverySucceeds() = runTest {
        val gate = DataMaintenanceGate()
        gate.maintenance { gate.requireRecovery() }
        assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gate.state.value)
        assertTrue(runCatching { gate.write { fail("must not execute") } }.exceptionOrNull() is DataMaintenanceBusyException)
        assertTrue(runCatching { gate.maintenance { fail("must not execute") } }.exceptionOrNull() is DataMaintenanceBusyException)
        gate.maintenance(recovery = true) { }
        assertEquals(DataMaintenanceGate.State.IDLE, gate.state.value)
        var executed = false
        gate.write { executed = true }
        assertTrue(executed)
    }

    @Test fun nestedPreferenceWriteUsesExistingPermitWithoutDeadlock() = runTest {
        val gate = DataMaintenanceGate()
        var saved = false
        gate.write { gate.write { saved = true } }
        assertTrue(saved)
    }

    @Test fun duplicateMaintenanceCannotQueueAndRunLater() = runTest {
        val gate = DataMaintenanceGate()
        val barrier = CompletableDeferred<Unit>()
        var secondRan = false
        val first = launch { gate.maintenance { barrier.await() } }
        runCurrent()
        val result = runCatching { gate.maintenance { secondRan = true } }
        assertTrue(result.exceptionOrNull() is DataMaintenanceBusyException)
        barrier.complete(Unit); first.join()
        assertFalse(secondRan)
    }
}