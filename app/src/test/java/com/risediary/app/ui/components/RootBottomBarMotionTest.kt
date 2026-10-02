/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drive the actual Animatable with frames, not a wall-clock approximation. */
@OptIn(ExperimentalCoroutinesApi::class)
class RootBottomBarMotionTest {
    private class Frames : MonotonicFrameClock {
        private var time = 0L
        private val waiters = mutableListOf<(Long) -> Unit>()
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            suspendCancellableCoroutine { continuation ->
                val waiter: (Long) -> Unit = { nanos ->
                    if (continuation.isActive) {
                        continuation.resumeWith(runCatching { onFrame(nanos) })
                    }
                }
                waiters += waiter
                continuation.invokeOnCancellation { waiters.remove(waiter) }
            }

        fun frame(afterMillis: Long) {
            time += afterMillis * 1_000_000L
            val ready = waiters.toList()
            waiters.clear()
            ready.forEach { it(time) }
        }
    }

    @Test fun delayedFirstFrameDoesNotSkipTheDescent() = runTest {
        val frames = Frames()
        val bar = RootBottomBarMotion(true)
        val hide = launch(frames) { bar.animateRootVisibility(false) }
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(0f, bar.value, 0f)
        frames.frame(2_000)
        runCurrent()
        assertEquals(0f, bar.value, 0f)
        frames.frame(150)
        runCurrent()
        assertTrue(bar.value > 0f && bar.value < 1f)
        val middle = bar.value
        advanceTimeBy(2_000)
        assertEquals(middle, bar.value, 0f)
        frames.frame(150)
        runCurrent()
        assertEquals(1f, bar.value, 0f)
        hide.join()
    }

    @Test fun revealUsesItsOwnClockAfterReturnConfirmation() = runTest {
        val frames = Frames()
        val bar = RootBottomBarMotion(false)
        bar.animateRootVisibility(false)
        advanceTimeBy(2_000)
        assertEquals(1f, bar.value, 0f)
        val reveal = launch(frames) { bar.animateRootVisibility(true) }
        runCurrent()
        assertEquals(1f, bar.value, 0f)
        frames.frame(2_000)
        runCurrent()
        assertEquals(1f, bar.value, 0f)
        frames.frame(130)
        runCurrent()
        assertTrue(bar.value > 0f && bar.value < 1f)
        frames.frame(130)
        runCurrent()
        assertEquals(0f, bar.value, 0f)
        reveal.join()
    }

    @Test fun openingAPageDuringRevealContinuesFromTheCurrentPosition() = runTest {
        val frames = Frames()
        val bar = RootBottomBarMotion(false)
        val reveal = launch(frames) { bar.animateRootVisibility(true) }
        runCurrent()
        frames.frame(0)
        runCurrent()
        frames.frame(120)
        runCurrent()
        val before = bar.value
        assertTrue(before > 0f && before < 1f)
        reveal.cancelAndJoin()
        val hide = launch(frames) { bar.animateRootVisibility(false) }
        runCurrent()
        assertEquals(before, bar.value, 0f)
        frames.frame(0)
        runCurrent()
        assertEquals(before, bar.value, 0.0001f)
        frames.frame(150)
        runCurrent()
        assertTrue(bar.value > before)
        frames.frame(150)
        runCurrent()
        assertEquals(1f, bar.value, 0f)
        hide.join()
    }

    @Test fun repeatedReversalsFinishAtTheLatestTargetWithoutStaleFrames() = runTest {
        val frames = Frames()
        val bar = RootBottomBarMotion(false)
        var animation = launch(frames) { bar.animateRootVisibility(true) }
        runCurrent()
        frames.frame(0)
        runCurrent()
        frames.frame(100)
        runCurrent()
        repeat(3) { index ->
            val before = bar.value
            animation.cancelAndJoin()
            animation = launch(frames) { bar.animateRootVisibility(index % 2 != 0) }
            runCurrent()
            assertEquals(before, bar.value, 0f)
            frames.frame(0)
            runCurrent()
            assertEquals(before, bar.value, 0.0001f)
            frames.frame(80)
            runCurrent()
        }
        frames.frame(300)
        runCurrent()
        assertEquals(1f, bar.value, 0f)
        animation.join()
    }
}
