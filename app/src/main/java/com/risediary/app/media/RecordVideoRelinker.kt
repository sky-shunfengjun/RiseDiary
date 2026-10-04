package com.risediary.app.media

import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import java.time.Clock
import javax.inject.Inject

/** Rebinds one saved record; permission ownership lasts until the write finishes. */
class RecordVideoRelinker @Inject constructor(
    private val flights: FlightRepository,
    private val grants: VideoGrantRegistry,
    private val gate: DataMaintenanceGate,
    private val clock: Clock
) {
    suspend fun relink(expected: Flight, generation: Long, owner: String, uri: String, flags: Int): Flight {
        try {
            return gate.write {
                gate.requireGeneration(generation)
                gate.requireCurrent(expected, flights.getById(expected.id))
                val video = grants.acquire(owner, uri, flags, setOfNotNull(expected.videoUri)).getOrThrow()
                val updated = expected.copy(videoUri = video.uriString,
                    videoDisplayName = video.displayName, videoMimeType = video.mimeType, updatedAt = clock.millis())
                flights.update(updated)
                updated
            }
        } finally {
            grants.forget(owner)
        }
    }
}
