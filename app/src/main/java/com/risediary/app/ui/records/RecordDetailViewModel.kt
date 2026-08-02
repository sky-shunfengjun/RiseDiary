package com.risediary.app.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlightRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    private val flightId: Long = checkNotNull(savedStateHandle["flightId"])

    private val _flight = MutableStateFlow<Flight?>(null)
    val flight: StateFlow<Flight?> = _flight.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _flight.value = repository.getById(flightId)
            _loading.value = false
        }
    }

    fun delete() {
        val current = _flight.value ?: return
        viewModelScope.launch {
            repository.delete(current)
            _deleted.value = true
            runCatching { reminderScheduler.onFlightDataChanged() }
        }
    }
}
