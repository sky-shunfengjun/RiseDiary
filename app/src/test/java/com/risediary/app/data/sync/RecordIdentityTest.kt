package com.risediary.app.data.sync

import com.risediary.app.data.entity.Flight
import org.junit.Assert.*
import org.junit.Test

class RecordIdentityTest {
    private fun flight() = Flight(startTime = 1_000L, endTime = 61_000L, durationSeconds = 60,
        spurtCount = null, semenVolumeMl = 2.3f, ejaculationDistanceCm = null,
        methodTags = "[]", moodNote = "", createdAt = 1_000L, updatedAt = 61_000L)

    @Test fun newlyCreatedRecordsHaveDifferentCanonicalIdsAndPhoneOrigin() {
        val first = flight()
        val second = flight()
        assertNotEquals(first.globalId, second.globalId)
        assertEquals(java.util.UUID.fromString(first.globalId).toString(), first.globalId)
        assertEquals("phone", first.recordSource)
        assertNull(first.sourceDeviceId)
        RecordIdentity.requireValidRecords(listOf(first, second))
    }

    @Test fun ordinaryCopiesKeepIdentityAndOrigin() {
        val original = flight().copy(recordSource = "wearable", sourceDeviceId = "band-app-1")
        val edited = original.copy(id = 19L, moodNote = "changed", updatedAt = 90_000L)
        assertEquals(original.globalId, edited.globalId)
        assertEquals(original.recordSource, edited.recordSource)
        assertEquals(original.sourceDeviceId, edited.sourceDeviceId)
    }

    @Test fun identityValidationDoesNotUseThePhoneLocalIdOrSubmissionKey() {
        val original = flight().copy(id = 1L, recordDraftId = "saved-submission")
        RecordIdentity.requireValidRecords(listOf(original, flight().copy(id = 1L)))
    }

    @Test fun duplicateGlobalIdsAreRejectedEvenWhenLocalIdsDiffer() {
        val original = flight().copy(id = 1L)
        assertThrows(IllegalArgumentException::class.java) {
            RecordIdentity.requireValidRecords(listOf(original, original.copy(id = 2L)))
        }
    }

    @Test fun blankMalformedUppercaseAndNilIdsAreRejected() {
        val uuid = "dc91848e-80cb-4a6f-8f8f-0d3b8d9c8927"
        for (invalid in listOf("", " ", "1", "1-1-1-1-1", uuid.uppercase(),
            "00000000-0000-0000-0000-000000000000", uuid + " ")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecordIdentity.requireValidRecords(listOf(flight().copy(globalId = invalid)))
            }
        }
    }

    @Test fun validExplicitIdentityIsPreserved() {
        val value = flight().copy(globalId = "dc91848e-80cb-4a6f-8f8f-0d3b8d9c8927", recordSource = "wearable")
        RecordIdentity.requireValidRecords(listOf(value))
        assertEquals("dc91848e-80cb-4a6f-8f8f-0d3b8d9c8927", value.globalId)
    }

    @Test fun unknownOriginsAreRejected() {
        for (source in listOf("", "PHONE", "watch", "future-source")) {
            assertThrows(IllegalArgumentException::class.java) {
                RecordIdentity.requireValidRecords(listOf(flight().copy(recordSource = source)))
            }
        }
    }

    @Test fun optionalDeviceIdentityAcceptsNullAndRejectsBlankOrOverlongValues() {
        RecordIdentity.requireValidRecords(listOf(flight(), flight().copy(sourceDeviceId = "a".repeat(128))))
        for (device in listOf("", " ", "a".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) {
                RecordIdentity.requireValidRecords(listOf(flight().copy(sourceDeviceId = device)))
            }
        }
    }
}
