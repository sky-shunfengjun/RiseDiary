package com.risediary.app.ui

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RetainedReadFlowTest {
    @Test fun readFailureKeepsLastContentAndRetryResubscribes() = runTest {
        var attempts = 0
        val source = flow<List<Int>> {
            attempts++
            if (attempts==1) { emit(listOf(7,8)); throw IOException("read failed") }
            emit(listOf(7,8,9))
        }
        val reader = RetainedReadFlow(backgroundScope,source,emptyList())
        runCurrent()
        assertTrue(reader.failed.value); assertEquals(listOf(7,8),reader.data.value)
        reader.retry(); runCurrent()
        assertFalse(reader.failed.value); assertEquals(listOf(7,8,9),reader.data.value)
        assertEquals(2,attempts)
    }
    @Test fun firstFailureIsNotEmittedAsAnEmptySuccessfulSnapshot() = runTest {
        val reader = RetainedReadFlow(backgroundScope,flow<List<Int>> { throw IOException("unavailable") },listOf(3))
        runCurrent(); assertTrue(reader.failed.value); assertEquals(listOf(3),reader.data.value)
    }
}
