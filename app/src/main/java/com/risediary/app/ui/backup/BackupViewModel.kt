package com.risediary.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.backup.BackupManager
import com.risediary.app.data.backup.BackupResult
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupState { IDLE, WORKING, SUCCESS, ERROR }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(BackupState.IDLE)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _showClearConfirm = MutableStateFlow(false)
    val showClearConfirm: StateFlow<Boolean> = _showClearConfirm.asStateFlow()

    val needsUserSelectedExportDestination: Boolean
        get() = manager.needsUserSelectedExportDestination

    fun defaultFilename(): String = manager.defaultFilename()
    fun showClearDialog() { _showClearConfirm.value = true }
    fun dismissClearDialog() { _showClearConfirm.value = false }

    fun exportBackup() = runOperation(operation = manager::exportToDownloads)
    fun exportBackupTo(uri: Uri) = runOperation {
        manager.exportToUri(uri)
    }
    fun importBackup(uri: Uri) = runOperation(refreshReminders = true) {
        manager.restoreFromUri(uri)
    }
    fun clearAllData() = runOperation(
        operation = manager::clearAll,
        refreshReminders = true
    )

    fun resetState() {
        _state.value = BackupState.IDLE
        _message.value = ""
    }

    private fun runOperation(
        refreshReminders: Boolean = false,
        operation: suspend () -> BackupResult
    ) {
        if (_state.value == BackupState.WORKING) return
        viewModelScope.launch {
            _state.value = BackupState.WORKING
            _message.value = "正在处理…"
            when (val result = operation()) {
                is BackupResult.Success -> {
                    if (refreshReminders) {
                        runCatching { reminderScheduler.onAllDataChanged() }
                    }
                    _state.value = BackupState.SUCCESS
                    _message.value = result.message
                }
                is BackupResult.Failure -> {
                    _state.value = BackupState.ERROR
                    _message.value = result.message
                }
            }
        }
    }
}
