package com.risediary.app.ui.backup

import android.content.Context
import com.risediary.app.R
import com.risediary.app.data.backup.BackupManager
import com.risediary.app.data.backup.BackupResult
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The operation belongs to the process, not to a navigated-away page. */
@Singleton
class BackupOperationCoordinator @Inject constructor(
    val manager: BackupManager,
    private val reminders: ReminderScheduler,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BackupState.IDLE)
    val state = mutableState.asStateFlow()
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    val maintenanceState get() = manager.maintenanceState

    fun start(refreshReminders: Boolean = false, operation: suspend () -> BackupResult) {
        if (mutableState.value == BackupState.WORKING) return
        mutableState.value = BackupState.WORKING
        mutableMessage.value = context.getString(R.string.backup_processing)
        scope.launch {
            val result = try { operation() } catch (failure: Exception) {
                BackupResult.Failure("操作未完成，请重试。", failure)
            }
            if (refreshReminders && manager.maintenanceState.value == com.risediary.app.data.DataMaintenanceGate.State.IDLE) {
                runCatching { reminders.onAllDataChanged() }
            }
            mutableMessage.value = when (result) {
                is BackupResult.Success -> result.message
                is BackupResult.Failure -> result.message
            }
            mutableState.value = if (result is BackupResult.Success) BackupState.SUCCESS else BackupState.ERROR
        }
    }
    fun reset() {
        if (mutableState.value == BackupState.WORKING) return
        mutableState.value = BackupState.IDLE
        mutableMessage.value = ""
    }
}