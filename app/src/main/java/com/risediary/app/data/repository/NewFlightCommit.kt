package com.risediary.app.data.repository

import com.risediary.app.data.entity.Flight

/** The caller holds the database transaction and the maintenance write permit. */
internal suspend fun commitNewFlightOnce(
    flight: Flight,
    readExisting: suspend (String) -> Flight?,
    insert: suspend (Flight) -> Long
): Flight {
    val submissionId = requireNotNull(flight.recordDraftId)
    require(submissionId.isNotBlank() && submissionId.length <= 128)
    require(flight.id == 0L)
    return readExisting(submissionId) ?: flight.copy(id = insert(flight))
}
