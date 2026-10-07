package com.risediary.app.data.backup

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.TagJson
import org.junit.Assert.*
import org.junit.Test

class RecordTagsBackupTest {
    @Test fun largeLegalTagCollectionsRoundTripWithoutTruncation() {
        for (count in listOf(101, 1000)) {
            val tags = (1..count).map { "方式$it" }
            val flight = Flight(startTime = 1000, endTime = 2000, durationSeconds = 1,
                spurtCount = null, semenVolumeMl = 1f, ejaculationDistanceCm = null,
                methodTags = TagJson.encode(tags), moodNote = "", createdAt = 1000, updatedAt = 1000)
            TagJson.validate(flight.methodTags)
            val restored = BackupJsonCodec.parseFlights(BackupJsonCodec.flightsToJson(listOf(flight)).toString()).single()
            assertEquals(tags, TagJson.decode(restored.methodTags))
            assertEquals(flight.methodTags, BackupJsonCodec.flightToJson(restored).getString("methodTags"))
        }
    }
    @Test fun invalidTagTypesAndTextAreRejected() {
        for (value in listOf("[1]", "[null]", "[true]", "[{}]", "[\" \"]", "[\"${"字".repeat(21)}\"]")) {
            assertThrows(IllegalArgumentException::class.java) { TagJson.validate(value) }
        }
    }
}
