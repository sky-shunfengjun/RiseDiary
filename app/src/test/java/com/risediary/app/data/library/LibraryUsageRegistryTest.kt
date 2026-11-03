package com.risediary.app.data.library

import org.junit.Assert.*
import org.junit.Test

class LibraryUsageRegistryTest {
    @Test fun activeOwnersAndDeletionReservationsExcludeEachOther() {
        val registry = LibraryUsageRegistry()
        val viewer = Any()
        assertTrue(registry.hold(viewer, "a"))
        assertThrows(IllegalStateException::class.java) { registry.reserveDeletion(setOf("a", "b")) }
        registry.release(viewer)
        val reservation = registry.reserveDeletion(setOf("a", "b"))
        assertFalse(registry.hold(viewer, "b"))
        reservation.close()
        assertTrue(registry.hold(viewer, "b"))
        registry.release(viewer)
    }

    @Test fun switchingOneOwnerDoesNotReleaseAnotherOwnersProtection() {
        val registry = LibraryUsageRegistry()
        val viewer = Any(); val form = Any()
        assertTrue(registry.hold(viewer, "a")); assertTrue(registry.hold(form, "a"))
        assertTrue(registry.hold(viewer, "b")); registry.release(form)
        registry.reserveDeletion(setOf("a")).close()
        assertThrows(IllegalStateException::class.java) { registry.reserveDeletion(setOf("b")) }
        registry.release(viewer)
        registry.reserveDeletion(setOf("b")).close()
    }
}
