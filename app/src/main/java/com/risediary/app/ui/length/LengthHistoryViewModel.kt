package com.risediary.app.ui.length

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LengthHistoryViewModel @Inject constructor(
    private val repo: LengthRecordRepository,
    private val achievementDetector: AchievementDetector,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val allRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
    val periodRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            allRecords.value = repo.getAll()
            periodRecords.value = allRecords.value.sortedBy(LengthRecord::recordDate)
        }
    }

    fun save(record: LengthRecord) {
        if (
            record.flaccidLengthCm !in 0.1f..100f ||
            record.erectLengthCm !in 0.1f..100f
        ) {
            _error.value = context.getString(R.string.length_error_range)
            return
        }
        viewModelScope.launch {
            val savedRecord = if (record.id == 0L) {
                val id = repo.insert(record)
                record.copy(id = id)
            } else {
                repo.update(record)
                record
            }
            achievementDetector.checkLengthAchievements(savedRecord)
            _error.value = null
            runCatching { reminderScheduler.onLengthDataChanged() }
            load()
        }
    }

    fun delete(record: LengthRecord) {
        viewModelScope.launch {
            repo.delete(record)
            runCatching { reminderScheduler.onLengthDataChanged() }
            load()
        }
    }

    fun clearError() {
        _error.value = null
    }
}
