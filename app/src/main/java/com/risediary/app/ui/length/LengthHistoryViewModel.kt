package com.risediary.app.ui.length

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class LengthHistoryViewModel @Inject constructor(
    private val repo: LengthRecordRepository,
    private val achievementDetector: AchievementDetector,
    private val clock: Clock,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val allRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
    val periodRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 0 = 30天, 1 = 90天, 2 = 全年 */
    var selectedPeriod by mutableStateOf(1)
        private set

    var periodLabel by mutableStateOf("近90天")
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            allRecords.value = repo.getAll()
            applyPeriod()
        }
    }

    fun selectPeriod(index: Int) {
        selectedPeriod = index
        periodLabel = when (index) {
            0 -> "近30天"
            1 -> "近90天"
            else -> "全年"
        }
        applyPeriod()
    }

    private fun applyPeriod() {
        val days = when (selectedPeriod) {
            0 -> 30
            1 -> 90
            else -> null
        }
        val since = days?.let { clock.millis() - it * 86400000L }
        periodRecords.value = allRecords.value
            .filter { since == null || it.recordDate >= since }
            .sortedBy(LengthRecord::recordDate)
    }

    fun save(record: LengthRecord) {
        if (
            record.flaccidLengthCm !in 0.1f..100f ||
            record.erectLengthCm !in 0.1f..100f
        ) {
            _error.value = "长度需要在 0.1 到 100 厘米之间"
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
}
