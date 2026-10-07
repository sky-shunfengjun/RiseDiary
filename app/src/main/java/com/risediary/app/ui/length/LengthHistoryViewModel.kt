package com.risediary.app.ui.length

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.DataWriteConflictException
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LengthHistoryViewModel internal constructor(
    private val repo: LengthRecordRepository,
    private val checkAchievements: suspend (LengthRecord) -> Unit,
    private val onDataChanged: suspend () -> Unit,
    private val rangeError: () -> String,
    private val maintenanceGate: DataMaintenanceGate = DataMaintenanceGate()
) : ViewModel() {
    @Inject constructor(
        repo: LengthRecordRepository,
        achievementDetector: AchievementDetector,
        reminderScheduler: ReminderScheduler,
        @ApplicationContext context: Context,
        maintenanceGate: DataMaintenanceGate
    ) : this(repo, { achievementDetector.checkLengthAchievements(it); Unit },
        reminderScheduler::onLengthDataChanged,
        { context.getString(R.string.length_error_range) }, maintenanceGate)

    val allRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
    val periodRecords = MutableStateFlow<List<LengthRecord>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()
    private val _pageError = MutableStateFlow<String?>(null)
    val pageError = _pageError.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private var retryPageOperation: (() -> Unit)? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        _loading.value = true
        setPageError(null)
        loadJob = viewModelScope.launch {
            try {
                val records = repo.getAll()
                currentCoroutineContext().ensureActive()
                if (generation != loadGeneration) return@launch
                allRecords.value = records
                periodRecords.value = records.sortedBy(LengthRecord::recordDate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == loadGeneration) {
                    setPageError("无法读取记录，请重试。", ::load)
                }
            } finally {
                if (generation == loadGeneration) _loading.value = false
            }
        }
    }

    val maintenanceState = maintenanceGate.state

    fun save(record: LengthRecord, original: LengthRecord? = null, onSaved: () -> Unit = {}) {
        // Set before launching: even two taps in one frame must have just one writer.
        if (_busy.value) return
        if (record.flaccidLengthCm !in 0.1f..100f || record.erectLengthCm !in 0.1f..100f) {
            setSaveError(rangeError())
            return
        }
        _busy.value = true
        setSaveError(null)
        var committed = false
        val job = maintenanceGate.launchWrite(viewModelScope) {
            try {
                // A cancelled editor must still receive a definite result if its DB write began.
                val savedRecord = withContext(NonCancellable) {
                    val saved = if (record.id == 0L) {
                        record.copy(id = repo.insert(record))
                    } else {
                        val expected = original ?: error("请重新打开这条记录后再保存。")
                        maintenanceGate.requireCurrent(expected, repo.getById(record.id))
                        record.copy(globalId = expected.globalId).also { repo.update(it) }
                    }
                    committed = true
                    setSaveError(null)
                    onSaved()
                    saved
                }
                currentCoroutineContext().ensureActive()
                load()
                optionalFollowUp { checkAchievements(savedRecord) }
                optionalFollowUp { onDataChanged() }
            } catch (cancelled: CancellationException) {
                if (!committed && cancelled is DataWriteConflictException) setSaveError(cancelled.message)
                throw cancelled
            } catch (_: Exception) {
                if (!committed) setSaveError("记录未保存，请重试；填写内容已保留。")
            }
        }
        // Includes rejection before the body starts, and cancellation while waiting for the gate.
        job.invokeOnCompletion { cause ->
            _busy.value = false
            if (!committed && cause is DataMaintenanceBusyException) setSaveError(cause.message)
        }
    }

    fun delete(record: LengthRecord) {
        if (_busy.value) return
        _busy.value = true
        setPageError(null)
        var committed = false
        val job = maintenanceGate.launchWrite(viewModelScope) {
            try {
                withContext(NonCancellable) {
                    repo.delete(record)
                    committed = true
                }
                currentCoroutineContext().ensureActive()
                load()
                optionalFollowUp { onDataChanged() }
            } catch (cancelled: CancellationException) {
                if (!committed && cancelled is DataWriteConflictException) {
                    setPageError(cancelled.message, ::load)
                }
                throw cancelled
            } catch (_: Exception) {
                if (!committed) setPageError("删除未完成，记录仍保留，请重试。") { delete(record) }
            }
        }
        job.invokeOnCompletion { cause ->
            _busy.value = false
            if (!committed && cause is DataMaintenanceBusyException) {
                setPageError(cause.message) { delete(record) }
            }
        }
    }

    fun retry() {
        if (_busy.value || _loading.value) return
        retryPageOperation?.invoke()
    }

    /** Dismissing the editor must not erase an independent list/read/delete failure. */
    fun clearError() { setSaveError(null) }

    private fun setSaveError(message: String?) {
        _saveError.value = message
        _error.value = message ?: _pageError.value
    }

    private fun setPageError(message: String?, retry: (() -> Unit)? = null) {
        _pageError.value = message
        retryPageOperation = retry
        _error.value = _saveError.value ?: message
    }

    private suspend fun optionalFollowUp(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The record already committed; a follow-up failure must never offer another insert.
        }
    }
}
