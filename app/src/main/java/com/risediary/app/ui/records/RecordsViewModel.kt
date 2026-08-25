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

    /** User timezone used for both filtering and grouping so they never disagree. */
    val userZoneId: ZoneId
        get() = zoneId

    val allFlights: StateFlow<List<Flight>> = flightRepo.allFlights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = tagRepo.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedTag by mutableStateOf<String?>(null)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    private val _pendingDeletions = MutableStateFlow<List<PendingDeletion>>(emptyList())
    internal val pendingDeletions: StateFlow<List<PendingDeletion>> = _pendingDeletions.asStateFlow()
    private val deletionOperations = PendingDeletionOperations()

    fun filterByTag(tagName: String?) {
        selectedTag = tagName
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        startDate = start
        endDate = end
    }

    /**
     * Registers the deletion synchronously (so the undo entry exists before the
     * Snackbar appears) and deletes from the database behind the serialized
     * operations mutex. Undo/finalize flip flags on the same entry, also under
     * the mutex, so delete and undo can never interleave on the same record.
     */
    fun delete(flight: Flight) {
        _pendingDeletions.update { enqueuePendingDeletion(it, flight) }
        viewModelScope.launch {
            deletionOperations.run {
                val entry = _pendingDeletions.value.firstOrNull { it.flight.id == flight.id }
                    ?: return@run
                if (!entry.cancelled && !entry.completed) {
                    flightRepo.delete(flight)
                    entry.completed = true
                }
                if (entry.finalized && entry.completed) {
                    removePendingDeletion(flight.id)
                }
            }
            runCatching { reminderScheduler.onFlightDataChanged() }
        }
    }

    fun undoDelete(flight: Flight) {
        viewModelScope.launch {
            deletionOperations.run {
                val entry = _pendingDeletions.value.firstOrNull { it.flight.id == flight.id }
                    ?: return@run
                if (entry.finalized) return@run
                if (entry.completed) {
                    flightRepo.insert(flight)
                } else {
                    entry.cancelled = true
                }
                removePendingDeletion(flight.id)
            }
            runCatching { reminderScheduler.onFlightDataChanged() }
        }
    }

    fun finalizeDeletion(flightId: Long) {
        viewModelScope.launch {
            deletionOperations.run {
                val entry = _pendingDeletions.value.firstOrNull { it.flight.id == flightId }
                    ?: return@run
                entry.finalized = true
                if (entry.completed) {
                    removePendingDeletion(flightId)
                }
            }
        }
    }

    fun clearPendingDeletions() {
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

/**
 * A deletion awaiting undo. [cancelled] is flipped by undo before the database
 * delete has run (delete then skips the DB), [completed] after the row is gone
 * (undo then re-inserts), [finalized] once the Snackbar expired (undo becomes
 * a no-op). All three flags are only mutated under [PendingDeletionOperations].
 */
internal class PendingDeletion(val flight: Flight) {
    var cancelled = false
    var completed = false
    var finalized = false
}

internal fun enqueuePendingDeletion(
    current: List<PendingDeletion>,
    flight: Flight
): List<PendingDeletion> =
    current.filterNot { it.flight.id == flight.id } + PendingDeletion(flight)

internal fun removePendingDeletion(
    current: List<PendingDeletion>,
    flightId: Long
): List<PendingDeletion> =
    current.filterNot { it.flight.id == flightId }

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
