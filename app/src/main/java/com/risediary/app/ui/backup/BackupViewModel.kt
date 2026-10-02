package com.risediary.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class BackupState { IDLE, WORKING, SUCCESS, ERROR }

@HiltViewModel
class BackupViewModel @Inject constructor(private val coordinator: BackupOperationCoordinator) : ViewModel() {
    val state = coordinator.state
    val message = coordinator.message
    val maintenanceState = coordinator.maintenanceState
    private val mutableConfirm = MutableStateFlow(false)
    val showClearConfirm = mutableConfirm.asStateFlow()
    val needsUserSelectedExportDestination get() = coordinator.manager.needsUserSelectedExportDestination
    fun defaultFilename() = coordinator.manager.defaultFilename()
    fun showClearDialog() { mutableConfirm.value = true }
    fun dismissClearDialog() { mutableConfirm.value = false }
    fun exportBackup() = coordinator.start(operation = coordinator.manager::exportToDownloads)
    fun exportBackupTo(uri: Uri) = coordinator.start { coordinator.manager.exportToUri(uri) }
    fun importBackup(uri: Uri) = coordinator.start(true) { coordinator.manager.restoreFromUri(uri) }
    fun clearAllData() = coordinator.start(true, coordinator.manager::clearAll)
    fun retryRecovery() = coordinator.start(true, coordinator.manager::retryRecovery)
    fun resetState() = coordinator.reset()
}