package com.risediary.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DataMaintenanceGateTest {
    @Test fun actionCapturedBeforeReplacementCannotStartAfterIt() = runTest {
        val gate = DataMaintenanceGate()
        var changed = false
        val stale = gate.launchWrite(this) { changed = true }
        gate.maintenance { }
        stale.join()
        assertTrue(stale.isCancelled)
        assertFalse(changed)
    }
    @Test fun rejectedActionNeverRunsSuccessCallbacks() = runTest {
        val gate = DataMaintenanceGate()
        gate.maintenance { gate.requireRecovery() }
        var success = false
        val rejected = gate.launchWrite(this) { success = true }
        rejected.join()
        assertTrue(rejected.isCancelled)
        assertFalse(success)
    }
    @Test fun ordinaryWriteFailureDoesNotLockOutLaterWrites() = runTest {
        val gate = DataMaintenanceGate()
        val failed = runCatching { gate.write { error("disk failure") } }
        assertTrue(failed.isFailure)
        assertEquals(DataMaintenanceGate.State.IDLE, gate.state.value)
        var saved = false
        gate.write { saved = true }
        assertTrue(saved)
    }
}