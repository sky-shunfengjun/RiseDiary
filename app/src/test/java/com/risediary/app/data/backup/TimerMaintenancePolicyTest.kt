package com.risediary.app.data.backup

import com.risediary.app.service.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TimerMaintenancePolicyTest {
    @Test fun failedStartCannotBeMistakenForAnIdleTimerDuringRestore() = runTest {
        val holder = TimerStateHolder()
        var persisted = TimerSession()
        val running = TimerSessionPolicy.start(persisted, TimerStartRequest("pending", TimerKind.NORMAL), 1000, 1000, 1)
        val committer = TimerSessionCommitter(
            save = { throw IOException("storage full") }, publish = holder::set, notify = {}, stop = {})
        assertTrue(runCatching { committer.commit(prepareTimerTransition(running), true) }.isFailure)
        holder.setPersistenceError(true)
        assertEquals(TimerStatus.IDLE, persisted.status)
        assertEquals(TimerStatus.IDLE, holder.state.value.status)
        val failure = runCatching { requireNoActiveTimerForMaintenance(persisted, holder.state.value, holder.persistenceError.value) }
        assertTrue("The pending start must be handled before replacing user data", failure.exceptionOrNull() is IllegalStateException)
        assertEquals(TimerStatus.IDLE, holder.state.value.status)
        holder.setPersistenceError(false)
        requireNoActiveTimerForMaintenance(persisted, holder.state.value, holder.persistenceError.value)
    }

    @Test fun eitherLiveOrPersistedActiveTimerBlocksReplacement() {
        for (status in listOf(TimerStatus.RUNNING, TimerStatus.PAUSED)) {
            val active = TimerSession(status = status, sessionId = "active", startedAtEpochMillis = 1000L)
            assertTrue(runCatching { requireNoActiveTimerForMaintenance(TimerSession(), active, false) }.isFailure)
            assertTrue(runCatching { requireNoActiveTimerForMaintenance(active, TimerSession(), false) }.isFailure)
        }
    }

    @Test fun cleanTerminalTimerDoesNotActLikeAnUnsavedForm() {
        for (status in listOf(TimerStatus.IDLE, TimerStatus.FINISHED, TimerStatus.LIMIT_REACHED)) {
            val ended = TimerSession(status = status, sessionId = if (status == TimerStatus.IDLE) null else "ended")
            requireNoActiveTimerForMaintenance(ended, ended, false)
            assertTrue(runCatching { requireNoActiveTimerForMaintenance(ended, ended, true) }.isFailure)
        }
    }
}
