package com.risediary.app.ui.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRefreshCoordinatorTest {
    @Test fun changesDuringQueryAreCoalescedAndStaleDataIsNeverPublished() = runTest {
        val first = CompletableDeferred<Int>()
        val second = CompletableDeferred<Int>()
        var queries = 0
        val published = mutableListOf<Int>()
        val coordinator = HomeRefreshCoordinator(backgroundScope,
            load = { if (++queries == 1) first.await() else second.await() },
            publish = { published += it })
        coordinator.request(silent = true)
        runCurrent()
        repeat(20) { coordinator.request(silent = true) }
        first.complete(1)
        runCurrent()
        assertEquals(2, queries)
        assertTrue(published.isEmpty())
        assertFalse(coordinator.isRefreshing.value)
        second.complete(2)
        runCurrent()
        assertEquals(listOf(2), published)
    }

    @Test fun failedQueriesKeepLastSuccessAndLaterRequestsStillRun() = runTest {
        var calls = 0
        val published = mutableListOf<Int>()
        val coordinator = HomeRefreshCoordinator(backgroundScope,
            load = { calls++; if (calls == 2) error("temporary read failure") else calls },
            publish = { published += it })
        coordinator.request(silent = true); runCurrent()
        assertFalse(coordinator.readFailed.value)
        coordinator.request(silent = true); runCurrent()
        assertTrue(coordinator.readFailed.value); assertEquals(listOf(1),published)
        coordinator.request(silent = true); runCurrent()
        assertFalse(coordinator.readFailed.value)
        assertEquals(listOf(1, 3), published)
    }

    @Test fun manualRequestDuringSilentQueryShowsSpinnerUntilFinalResult() = runTest {
        val first = CompletableDeferred<Int>()
        var calls = 0
        val coordinator = HomeRefreshCoordinator(backgroundScope,
            load = { if (++calls == 1) first.await() else 2 }, publish = {})
        coordinator.request(silent = true)
        runCurrent()
        coordinator.request(silent = false)
        assertTrue(coordinator.isRefreshing.value)
        first.complete(1)
        runCurrent()
        assertFalse(coordinator.isRefreshing.value)
        assertEquals(2, calls)
    }
}
