package com.risediary.app.media

import org.junit.Assert.*
import org.junit.Test

class VideoResourceCoordinatorTest {
    @Test fun secondPageRevokesFirstBeforePreparingAndOldReleaseCannotRevokeNewOwner() {
        val coordinator = VideoResourceCoordinator()
        val a = Any(); val b = Any(); val c = Any()
        val events = mutableListOf<String>()
        coordinator.acquire(a) { events += "release a"; coordinator.release(a) }
        events += "prepare a"
        coordinator.acquire(b) { events += "release b" }
        events += "prepare b"
        coordinator.release(a)
        coordinator.acquire(c) { events += "release c" }
        assertEquals(listOf("prepare a", "release a", "prepare b", "release b"), events)
    }
    @Test fun fullscreenOrOrientationKeepsTheSameLease() {
        val coordinator = VideoResourceCoordinator()
        val page = Any(); var releases = 0
        repeat(20) { coordinator.acquire(page) { releases++ } }
        assertEquals(0, releases)
        coordinator.release(page)
        coordinator.acquire(Any()) { }
        assertEquals(0, releases)
    }
    @Test fun diagnosticsAreOptInAndDiscardPreviousSessionOnDisable() {
        val diagnostics = VideoDiagnostics()
        val token = Any()
        diagnostics.attach(token, true)
        diagnostics.update(token) { it.copy(decoder = "test.decoder", droppedFrames = 4) }
        assertNull(diagnostics.snapshot.value.decoder)
        diagnostics.setEnabled(true)
        diagnostics.update(token) { it.copy(decoder = "test.decoder", width = 1920, height = 1080) }
        assertTrue(diagnostics.snapshot.value.report().contains("1920×1080"))
        diagnostics.setEnabled(false)
        assertEquals(VideoDiagnosticSnapshot(), diagnostics.snapshot.value)
    }
}
