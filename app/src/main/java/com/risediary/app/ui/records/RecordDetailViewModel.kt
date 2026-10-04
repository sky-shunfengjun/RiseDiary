package com.risediary.app.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.DataWriteConflictException
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.media.RecordVideoRelinker
import com.risediary.app.media.VideoAccessState
import com.risediary.app.media.VideoFileAccess
import com.risediary.app.media.VideoGrantRegistry
import com.risediary.app.media.localVideoRef
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val repository: FlightRepository,
    private val reminderScheduler: ReminderScheduler,
    private val maintenanceGate: DataMaintenanceGate,
    private val videoGrants: VideoGrantRegistry,
    private val videoFiles: VideoFileAccess,
    private val relinker: RecordVideoRelinker
) : ViewModel() {
    private var flightId: Long? = null
    private var readJob: Job? = null
    private var refreshEpoch = 0L
    private var loadedGeneration = 0L
    private data class Selection(val flight: Flight, val generation: Long)
    private var selection: Selection? = null
    private val owner = "detail:" + UUID.randomUUID()

    private val _flight = MutableStateFlow<Flight?>(null)
    val flight = _flight.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted = _deleted.asStateFlow()
    private val _videoAccess = MutableStateFlow<VideoAccessState?>(null)
    val videoAccess = _videoAccess.asStateFlow()
    private val _videoBusy = MutableStateFlow(false)
    val videoBusy = _videoBusy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load(id: Long) {
        flightId = id
        refresh()
    }

    fun refresh() {
        val id = flightId ?: return
        if (selection != null || _videoBusy.value && readJob?.isActive != true) return
        val epoch = ++refreshEpoch
        readJob?.cancel()
        readJob = viewModelScope.launch {
            _loading.value = _flight.value == null
            _videoBusy.value = true
            _error.value = null
            val generation = maintenanceGate.snapshotGeneration()
            try {
                val current = repository.getById(id)
                if (epoch != refreshEpoch || generation != maintenanceGate.snapshotGeneration()) return@launch
                _flight.value = current
                loadedGeneration = generation
                _videoAccess.value = null
                current?.localVideoRef()?.let { video ->
                    val state = videoFiles.check(video)
                    if (epoch == refreshEpoch && generation == maintenanceGate.snapshotGeneration()) {
                        _videoAccess.value = state
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == refreshEpoch) _error.value = "无法读取，请重试"
            } finally {
                if (epoch == refreshEpoch) {
                    _loading.value = false
                    _videoBusy.value = false
                }
            }
        }
    }

    fun beginVideoSelection(): Boolean {
        val current = _flight.value ?: return false
        if (_loading.value || _videoBusy.value || current.videoUri == null) return false
        selection = Selection(current, loadedGeneration)
        _videoBusy.value = true
        _error.value = null
        return true
    }

    fun cancelVideoSelection() {
        if (selection == null) return
        selection = null
        _videoBusy.value = false
        refresh()
    }

    fun selectVideo(uri: String, flags: Int) {
        val target = selection ?: return
        selection = null
        viewModelScope.launch {
            try {
                _flight.value = relinker.relink(target.flight, target.generation, owner, uri, flags)
                _videoAccess.value = VideoAccessState.READABLE
            } catch (_: DataMaintenanceBusyException) {
                _error.value = "数据已更新，请重新打开记录"
            } catch (_: DataWriteConflictException) {
                _error.value = "记录已变化，请重新打开后重试"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = "重新关联失败，原关联已保留，请重试"
            } finally {
                _videoBusy.value = false
            }
        }
    }

    fun delete() {
        val current = _flight.value ?: return
        if (_loading.value || _videoBusy.value) return
        _videoBusy.value = true
        viewModelScope.launch {
            try {
                maintenanceGate.write {
                    maintenanceGate.requireGeneration(loadedGeneration)
                    repository.delete(current)
                    _deleted.value = true
                    videoGrants.requestCleanup()
                    runCatching { reminderScheduler.onFlightDataChanged() }
                }
            } catch (_: DataMaintenanceBusyException) {
                _error.value = "数据已更新，请重新打开记录"
            } catch (_: DataWriteConflictException) {
                _error.value = "记录已变化，请重新打开后重试"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = "删除失败，请重试"
            } finally { _videoBusy.value = false }
        }
    }

    override fun onCleared() { videoGrants.forget(owner) }
}
