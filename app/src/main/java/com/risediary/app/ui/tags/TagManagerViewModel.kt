package com.risediary.app.ui.tags

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.entity.Tag
import com.risediary.app.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.risediary.app.data.DataMaintenanceGate

@HiltViewModel
class TagManagerViewModel @Inject constructor(
    private val repository: TagRepository,
    @ApplicationContext private val context: Context,
    private val maintenanceGate: DataMaintenanceGate = DataMaintenanceGate()
) : ViewModel() {
    private val reads = com.risediary.app.ui.RetainedReadFlow(viewModelScope, repository.allTags, emptyList())
    val tags = reads.data
    val readFailed = reads.failed
    fun retryRead() = reads.retry()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun save(existing: Tag?, name: String, color: String, onSaved: () -> Unit) {
        if (_isSaving.value) return
        val normalized = name.trim()
        if (normalized.length !in 1..20) {
            _error.value = context.getString(R.string.tag_manager_error_name_length)
            return
        }
        if (!color.matches(Regex("#[0-9A-Fa-f]{6}"))) {
            _error.value = context.getString(R.string.tag_manager_error_color)
            return
        }

        perform {
            val duplicate = repository.getAll().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            require(duplicate == null || duplicate.id == existing?.id) { context.getString(R.string.tag_manager_error_duplicate) }
            if (existing == null) repository.insert(Tag(name = normalized, color = color.uppercase(), sortOrder = tags.value.size))
            else {
                maintenanceGate.requireCurrent(existing, repository.getById(existing.id))
                repository.update(existing.copy(name = normalized, color = color.uppercase()))
            }
            onSaved()
        }
    }

    fun delete(tag: Tag, onDeleted: () -> Unit = {}) {
        perform {
            maintenanceGate.requireCurrent(tag, repository.getById(tag.id))
            repository.delete(tag)
            onDeleted()
        }
    }

    private var retryOperation: (() -> Unit)? = null
    fun retryWrite() { retryOperation?.invoke() }
    fun clearError() { _error.value = null; retryOperation = null }
    private fun perform(expectedGeneration: Long = maintenanceGate.snapshotGeneration(),
        onRejected: () -> Unit = {}, work: suspend () -> Unit) {
        if (_isSaving.value || readFailed.value) return
        _isSaving.value = true
        _error.value = null
        retryOperation = null
        viewModelScope.launch {
            try {
                maintenanceGate.write { maintenanceGate.requireGeneration(expectedGeneration); work() }
            } catch (conflict: com.risediary.app.data.DataWriteConflictException) {
                _error.value = "标签已变化，请重新打开后再试"; onRejected()
            } catch (busy: com.risediary.app.data.DataMaintenanceBusyException) {
                _error.value = "数据已更新或正在处理，请重新打开后再试"; onRejected()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                _error.value = failure.message?.takeIf(String::isNotBlank) ?: context.getString(R.string.tag_manager_error_save_failed)
                retryOperation = { perform(expectedGeneration, onRejected, work) }
                onRejected()
            } finally { _isSaving.value = false }
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = tags.value
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
            return
        }
        val reordered = current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }.mapIndexed { index, tag -> tag.copy(sortOrder = index) }
        perform {
            maintenanceGate.requireCurrent(current.map { it.copy(sortOrder = 0) }.sortedBy(Tag::id), repository.getAll().map { it.copy(sortOrder = 0) }.sortedBy(Tag::id))
            repository.updateAll(reordered)
        }
    }

    fun reorder(ordered: List<Tag>, onRejected: () -> Unit = {}) {
        val normalized = ordered.mapIndexed { index, tag ->
            tag.copy(sortOrder = index)
        }
        perform(onRejected = onRejected) {
            maintenanceGate.requireCurrent(ordered.map { it.copy(sortOrder = 0) }.sortedBy(Tag::id),
                repository.getAll().map { it.copy(sortOrder = 0) }.sortedBy(Tag::id))
            repository.updateAll(normalized)
        }
    }
}
