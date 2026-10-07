package com.risediary.app.ui

import com.risediary.app.service.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProjectedStateFlowTest {
    @Test fun controlsHaveCurrentInitialStateAndSkipClockAndProgressPolling() = runTest {
        val initial = TimerSession(status = TimerStatus.RUNNING, elapsedMillis = 2500L)
        val source = MutableStateFlow(initial)
        val projected = source.projectState { it.forControls() }
        assertEquals(TimerStatus.RUNNING, projected.value.status)
        val seen = mutableListOf<TimerSession>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { projected.collect { seen += it } }
        repeat(8) { source.value = initial.copy(elapsedMillis = 2500L + it * 200L); runCurrent() }
        assertEquals(1, seen.size)
        source.value = source.value.copy(status = TimerStatus.PAUSED); runCurrent()
        assertEquals(2, seen.size)
        assertEquals(TimerStatus.PAUSED, projected.value.status)
    }
    @Test fun localSecondsHaveCurrentInitialClockAndEmitOnlyOnSecondChanges() = runTest {
        val initial = TimerSession(status = TimerStatus.RUNNING, elapsedMillis = 2500L)
        val source = MutableStateFlow(initial)
        val projected = source.projectState { it.copy(elapsedMillis = it.elapsedMillis / 1000L * 1000L) }
        assertEquals(2000L, projected.value.elapsedMillis)
        val seen = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { projected.collect { seen += it.elapsedMillis } }
        (1..5).forEach { source.value = initial.copy(elapsedMillis = 2500L + it * 200L); runCurrent() }
        assertEquals(listOf(2000L, 3000L), seen)
        assertEquals(3000L, projected.value.elapsedMillis)
    }
}
