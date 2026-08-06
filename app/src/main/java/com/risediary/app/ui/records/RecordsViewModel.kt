package com.risediary.app.ui.records

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.TagJson
import com.risediary.app.data.repository.TagRepository
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val flightRepo: FlightRepository,
    tagRepo: TagRepository,
    private val zoneId: ZoneId,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val allFlights: StateFlow<List<Flight>> = flightRepo.allFlights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = tagRepo.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedTag by mutableStateOf<String?>(null)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    private val _pendingDeletions = MutableStateFlow<List<Flight>>(emptyList())
    val pendingDeletions: StateFlow<List<Flight>> = _pendingDeletions.asStateFlow()
    private val deletionSession = PendingDeletionSession()
    private val deletionOperations = PendingDeletionOperations()

    fun filterByTag(tagName: String?) {
        selectedTag = tagName
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        startDate = start
        endDate = end
    }

    fun delete(flight: Flight) {
        val session = deletionSession.capture()
        viewModelScope.launch {
            deletionOperations.run {
                flightRepo.delete(flight)
                if (deletionSession.isCurrent(session)) {
                    _pendingDeletions.update { enqueuePendingDeletion(it, flight) }
                }
            }
            runCatching { reminderScheduler.onFlightDataChanged() }
        }
    }

    fun undoDelete(flight: Flight) {
        viewModelScope.launch {
            if (_pendingDeletions.value.none { it.id == flight.id }) return@launch
            flightRepo.insert(flight)
            removePendingDeletion(flight.id)
            runCatching { reminderScheduler.onFlightDataChanged() }
        }
    }

    fun finalizeDeletion(flightId: Long) {
        removePendingDeletion(flightId)
    }

    fun clearPendingDeletions() {
        deletionSession.clear()
        _pendingDeletions.value = emptyList()
    }

    private fun removePendingDeletion(flightId: Long) {
        _pendingDeletions.update { removePendingDeletion(it, flightId) }
    }

    fun filter(flights: List<Flight>): List<Flight> {
        return flights.filter { flight ->
            val localDate = Instant.ofEpochMilli(flight.startTime).atZone(zoneId).toLocalDate()
            val matchesTag = selectedTag?.let { it in TagJson.decode(flight.methodTags) } ?: true
            val matchesStart = startDate?.let { !localDate.isBefore(it) } ?: true
            val matchesEnd = endDate?.let { !localDate.isAfter(it) } ?: true
            matchesTag && matchesStart && matchesEnd
        }
    }
}

internal fun enqueuePendingDeletion(current: List<Flight>, flight: Flight): List<Flight> =
    current.filterNot { it.id == flight.id } + flight

internal fun removePendingDeletion(current: List<Flight>, flightId: Long): List<Flight> =
    current.filterNot { it.id == flightId }

internal class PendingDeletionSession {
    private var generation = 0L

    fun capture(): Long = generation

    fun clear() {
        generation++
    }

    fun isCurrent(capturedGeneration: Long): Boolean =
        capturedGeneration == generation
}

internal class PendingDeletionOperations {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
