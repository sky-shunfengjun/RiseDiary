package com.risediary.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PreparedMaintenanceTest {
    @Test fun changedPreviewDoesNotInvalidatePendingFormsOrRunCommit() = runTest {
        val gate=DataMaintenanceGate()
        val before=gate.snapshotGeneration()
        var commit=false
        val result=gate.preparedMaintenance(precheck={"changed"}) { commit=true; "committed" }
        assertEquals("changed",result); assertFalse(commit)
        assertEquals(before,gate.snapshotGeneration()); assertEquals(DataMaintenanceGate.State.IDLE,gate.state.value)
        gate.write { gate.requireGeneration(before) }
    }
    @Test fun confirmedPreviewEntersMaintenanceAndInvalidatesOldWriters() = runTest {
        val gate=DataMaintenanceGate(); val before=gate.snapshotGeneration()
        gate.preparedMaintenance<Unit>(precheck={null}) { assertEquals(DataMaintenanceGate.State.WORKING,gate.state.value) }
        assertEquals(before+1,gate.snapshotGeneration()); assertEquals(DataMaintenanceGate.State.IDLE,gate.state.value)
    }
}
