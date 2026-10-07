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
import com.risediary.app.util.LocalCalendarContext
import javax.inject.Inject
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.media.VideoGrantRegistry
import java.util.UUID

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val flightRepo: FlightRepository,
    tagRepo: TagRepository,
    private val calendar: LocalCalendarContext,
    private val reminderScheduler: ReminderScheduler,
    private val maintenanceGate: DataMaintenanceGate = DataMaintenanceGate(),
    private val videoGrants: VideoGrantRegistry
) : ViewModel() {

    val calendarState = calendar.state

    private val flightReads = com.risediary.app.ui.RetainedReadFlow(viewModelScope,flightRepo.allFlights,emptyList())
    private val tagReads = com.risediary.app.ui.RetainedReadFlow(viewModelScope,tagRepo.allTags,emptyList())
    val allFlights: StateFlow<List<Flight>> = flightReads.data
    val tags = tagReads.data
    val readFailed = combine(flightReads.failed,tagReads.failed) { flights, tags -> flights || tags }
        .stateIn(viewModelScope,SharingStarted.Eagerly,false)
    fun retryRead() { flightReads.retry(); tagReads.retry() }

    var selectedTag by mutableStateOf<String?>(null)
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    private val _pendingDeletions = MutableStateFlow<List<PendingDeletion>>(emptyList())
    internal val pendingDeletions: StateFlow<List<PendingDeletion>> = _pendingDeletions.asStateFlow()
    private val deletionOperations = PendingDeletionOperations()
    private val deletionOwner = "deletion:" + UUID.randomUUID()
    private fun videoOwner(entry: PendingDeletion) = "$deletionOwner:${entry.token}"

    // Main.immediate may emit the current maintenance state during construction.
    // Initialize pending entries before the collector is allowed to clear them.
    init {
        viewModelScope.launch {
            maintenanceGate.state.collect { if (it != DataMaintenanceGate.State.IDLE) clearPendingDeletions() }
        }
    }

    fun filterByTag(tagName: String?) {
        selectedTag = tagName
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        startDate = start
        endDate = end
    }

    private val writeFailure = MutableStateFlow<String?>(null)
    val writeError = writeFailure.asStateFlow()
    private val writing = MutableStateFlow(false)
    val isWriting = writing.asStateFlow()
    private var retryAction: (() -> Unit)? = null
    private val retryAvailable = MutableStateFlow(false)
    val canRetryWrite = retryAvailable.asStateFlow()
    fun clearWriteError() { writeFailure.value = null; retryAction = null; retryAvailable.value = false }
    fun retryWrite() { if (!writing.value) retryAction?.invoke() }

    fun delete(flight: Flight, onDeleted: () -> Unit = {}): Boolean {
        if (writing.value) return false
        return deleteWithGeneration(flight, maintenanceGate.snapshotGeneration(), onDeleted)
    }

    private fun deleteWithGeneration(flight: Flight, generation: Long, onDeleted: () -> Unit): Boolean {
        if (writing.value) return false
        writing.value = true
        clearWriteError()
        viewModelScope.launch {
            try {
                deleteForUndo(flight, generation)
                onDeleted()
                try { reminderScheduler.onFlightDataChanged() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* Primary write succeeded; reminders refresh on the next change. */ }
            } catch (conflict: com.risediary.app.data.DataWriteConflictException) {
                writeFailure.value = "记录已变化，请重新打开后再删除"
            } catch (busy: com.risediary.app.data.DataMaintenanceBusyException) {
                writeFailure.value = "数据已更新或正在处理，请重新打开后再删除"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                retryAction = { deleteWithGeneration(flight, generation, onDeleted) }
                retryAvailable.value = true
                writeFailure.value = "删除失败，记录已保留，请重试"
            } finally { writing.value = false }
        }
        return true
    }

    fun undoDelete(flight: Flight, expectedToken: String? = null) {
        if (writing.value) return
        val original = _pendingDeletions.value.firstOrNull { it.flight == flight && (expectedToken == null || it.token == expectedToken) } ?: return
        writing.value = true
        clearWriteError()
        viewModelScope.launch {
            try {
                maintenanceGate.write {
                    maintenanceGate.requireGeneration(original.generation)
                    deletionOperations.run {
                        val entry = _pendingDeletions.value.firstOrNull { it === original } ?: return@run
                        if (entry.finalized) return@run
                        if (entry.completed) {
                            check(flightRepo.getById(flight.id) == null) { "记录已变化，无法撤销" }
                            // ABORT on identity collisions; undo must never replace a later record.
                            flightRepo.restoreDeleted(entry.flight)
                        } else entry.cancelled = true
                        removePendingDeletion(flight.id)
                    }
                }
                try { reminderScheduler.onFlightDataChanged() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { }
            } catch (busy: com.risediary.app.data.DataMaintenanceBusyException) {
                discardExpiredDeletions()
                writeFailure.value = "数据已更新，此次撤销已失效"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                retryAction = { undoDelete(original.flight, original.token) }
                retryAvailable.value = true
                writeFailure.value = "撤销失败，请重试"
            } finally { writing.value = false }
        }
    }

    /** The detail page waits for this write before leaving; the app owns the undo window. */
    suspend fun deleteForUndo(flight: Flight, expectedGeneration: Long) {
        maintenanceGate.write {
            maintenanceGate.requireGeneration(expectedGeneration)
            deletionOperations.run {
                discardExpiredDeletions()
                maintenanceGate.requireCurrent(flight, flightRepo.getById(flight.id))
                check(_pendingDeletions.value.none { it.flight.id == flight.id })
                _pendingDeletions.update { enqueuePendingDeletion(it, flight, expectedGeneration) }
                val entry = _pendingDeletions.value.first { it.flight.id == flight.id }
                try {
                    videoGrants.retain(videoOwner(entry), setOfNotNull(flight.videoUri))
                    flightRepo.delete(flight)
                    entry.completed = true
                    if (_pendingDeletions.value.none { it === entry }) videoGrants.forget(videoOwner(entry))
                } catch (failure: Exception) {
                    removePendingDeletion(flight.id)
                    throw failure
                }
            }
        }
    }

    fun finalizeDeletion(flightId: Long, expectedToken: String? = null) {
        viewModelScope.launch {
            deletionOperations.run {
                val entry = _pendingDeletions.value.firstOrNull { it.flight.id == flightId && (expectedToken == null || it.token == expectedToken) } ?: return@run
                entry.finalized = true
                if (entry.completed) removePendingDeletion(flightId)
            }
        }
    }

    fun clearPendingDeletions() {
        _pendingDeletions.value.forEach { videoGrants.forget(videoOwner(it)) }
        _pendingDeletions.value = emptyList()
    }

    private fun removePendingDeletion(flightId: Long) {
        val removed = _pendingDeletions.value.filter { it.flight.id == flightId }
        _pendingDeletions.update { removePendingDeletion(it, flightId) }
        removed.forEach { videoGrants.forget(videoOwner(it)) }
    }

    private fun discardExpiredDeletions() {
        val generation = maintenanceGate.snapshotGeneration()
        _pendingDeletions.value.filter { it.generation != generation }.forEach { removePendingDeletion(it.flight.id) }
    }

    override fun onCleared() { clearPendingDeletions() }

    fun filter(flights: List<Flight>, zoneId: java.time.ZoneId = calendar.current().zoneId): List<Flight> {
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
internal class PendingDeletion(val flight: Flight, val generation: Long = 0L) {
    val token = UUID.randomUUID().toString()
    var cancelled = false
    var completed = false
    var finalized = false
}

internal fun enqueuePendingDeletion(
    current: List<PendingDeletion>,
    flight: Flight,
    generation: Long = 0L
): List<PendingDeletion> =
    current.filterNot { it.flight.id == flight.id } + PendingDeletion(flight, generation)

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
