package com.risediary.app.ui.backup

import android.content.Context
import android.net.Uri
import com.risediary.app.data.backup.*
import com.risediary.app.R
import com.risediary.app.data.backup.BackupManager
import com.risediary.app.data.backup.BackupResult
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
    private val mutablePreview = MutableStateFlow<RestorePreview?>(null)
    val preview = mutablePreview.asStateFlow()
    private val mutablePreviewMessage = MutableStateFlow("")
    val previewMessage = mutablePreviewMessage.asStateFlow()
    private var previewJob: Job? = null
    private var previewToken = 0L
    private var committing = false
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
    fun prepare(uri: Uri) {
        if (mutableState.value == BackupState.WORKING) return
        val token = ++previewToken
        mutablePreviewMessage.value = ""
        mutableState.value = BackupState.WORKING; mutableMessage.value = "正在校验备份"
        previewJob = scope.launch {
            try {
                val result = manager.prepareRestore(uri,RestoreMode.MERGE)
                if (token == previewToken) { mutablePreview.value = result; mutableState.value = BackupState.IDLE }
                else { manager.discardRestore(result.preparationId) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (token != previewToken) return@launch
                mutableMessage.value = "备份未通过校验："+(failure.message ?: "请重新选择文件")
                mutableState.value = BackupState.ERROR
            }
        }
    }
    fun changeMode(mode: RestoreMode) {
        val previous = mutablePreview.value ?: return
        if (mutableState.value == BackupState.WORKING) return
        val token = previewToken
        mutablePreviewMessage.value = ""
        mutableState.value = BackupState.WORKING
        previewJob = scope.launch {
            try {
                val result = manager.changeRestoreMode(previous.preparationId,mode)
                if (token == previewToken) { mutablePreview.value = result; mutableState.value = BackupState.IDLE }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (token != previewToken) return@launch
                // A failed calculation invalidates the candidate; never confirm old counts.
                mutablePreview.value = null
                manager.discardRestore(previous.preparationId)
                mutableMessage.value = failure.message ?: "无法生成恢复预览"
                mutableState.value = BackupState.ERROR
            }
        }
    }
    fun confirmPreview() {
        val current = mutablePreview.value ?: return
        if (mutableState.value == BackupState.WORKING || committing) return
        committing = true; mutableState.value = BackupState.WORKING
        scope.launch {
            try {
                when (val result = manager.confirmRestore(current.preparationId,current.revision)) {
                    is RestoreConfirmation.Changed -> {
                        mutablePreview.value = result.preview
                        mutablePreviewMessage.value = "手机数据已变化，请检查新概览再确认"
                        mutableState.value = BackupState.IDLE
                    }
                    is RestoreConfirmation.Finished -> {
                        mutablePreview.value = null
                        if (manager.maintenanceState.value == com.risediary.app.data.DataMaintenanceGate.State.IDLE)
                            runCatching { reminders.onAllDataChanged() }
                        mutableMessage.value = when (val finished = result.result) {
                            is BackupResult.Success -> finished.message
                            is BackupResult.Failure -> finished.message
                        }
                        mutableState.value = if (result.result is BackupResult.Success) BackupState.SUCCESS else BackupState.ERROR
                    }
                }
            } catch (failure: Exception) {
                mutablePreviewMessage.value = failure.message ?: "恢复未完成，请重试"
                mutableState.value = BackupState.IDLE
            } finally { committing = false }
        }
    }
    fun discardPreview() {
        if (committing) return
        previewToken++
        if (previewJob?.isActive == true) { previewJob?.cancel(); mutableState.value = BackupState.IDLE }
        mutablePreviewMessage.value = ""
        val current = mutablePreview.value
        mutablePreview.value = null
        if (current != null) scope.launch { manager.discardRestore(current.preparationId) }
    }

    fun reset() {
        if (mutableState.value == BackupState.WORKING) return
        mutableState.value = BackupState.IDLE
        mutableMessage.value = ""
    }
}