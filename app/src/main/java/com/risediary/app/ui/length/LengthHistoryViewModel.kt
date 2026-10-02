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
import com.risediary.app.data.DataMaintenanceGate

@HiltViewModel
class LengthHistoryViewModel @Inject constructor(
    private val repo: LengthRecordRepository,
    private val achievementDetector: AchievementDetector,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context,
    private val maintenanceGate: DataMaintenanceGate = DataMaintenanceGate()
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

    val maintenanceState = maintenanceGate.state

    fun save(record: LengthRecord, original: LengthRecord? = null, onSaved: () -> Unit = {}) {
        if (
            record.flaccidLengthCm !in 0.1f..100f ||
            record.erectLengthCm !in 0.1f..100f
        ) {
            _error.value = context.getString(R.string.length_error_range)
            return
        }
        maintenanceGate.launchWrite(viewModelScope) {
            try {
                val savedRecord = if (record.id == 0L) {
                    val id = repo.insert(record)
                    record.copy(id = id)
                } else {
                    val expected = original ?: error("请重新打开这条记录后再保存。")
                    maintenanceGate.requireCurrent(expected, repo.getById(record.id))
                    repo.update(record)
                    record
                }
                runCatching { achievementDetector.checkLengthAchievements(savedRecord) }
                _error.value = null
                onSaved()
                runCatching { reminderScheduler.onLengthDataChanged() }
                load()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (cancelled is com.risediary.app.data.DataWriteConflictException) _error.value = cancelled.message
                throw cancelled
            } catch (_: Exception) {
                _error.value = "记录未保存，请重试；草稿已保留。"
            }
        }
    }

    fun delete(record: LengthRecord) {
        maintenanceGate.launchWrite(viewModelScope) {
            repo.delete(record)
            runCatching { reminderScheduler.onLengthDataChanged() }
            load()
        }
    }

    fun clearError() {
        _error.value = null
    }
}
