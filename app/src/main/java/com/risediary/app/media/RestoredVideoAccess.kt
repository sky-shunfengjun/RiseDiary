package com.risediary.app.media

import com.risediary.app.data.entity.Flight
import kotlinx.coroutines.CancellationException

/** Inspects restored references without granting access or discarding saved records. */
suspend fun VideoFileAccess.countUnavailableVideos(flights: List<Flight>): Int {
    val readable = mutableMapOf<String, Boolean>()
    var count = 0
    for (flight in flights) {
        val video = flight.localVideoRef() ?: continue
        val available = readable[video.uriString] ?: try {
            (check(video) == VideoAccessState.READABLE).also { readable[video.uriString] = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false.also { readable[video.uriString] = it }
        }
        if (!available) count++
    }
    return count
}
