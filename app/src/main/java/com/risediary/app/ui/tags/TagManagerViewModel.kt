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

@HiltViewModel
class TagManagerViewModel @Inject constructor(
    private val repository: TagRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val tags: StateFlow<List<Tag>> = repository.allTags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

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

        _isSaving.value = true
        viewModelScope.launch {
            try {
                runCatching {
                    val duplicate = repository.getAll().firstOrNull {
                        it.name.equals(normalized, ignoreCase = true)
                    }
                    if (duplicate != null && duplicate.id != existing?.id) {
                        error(context.getString(R.string.tag_manager_error_duplicate))
                    }
                    if (existing == null) {
                        repository.insert(
                            Tag(
                                name = normalized,
                                color = color.uppercase(),
                                sortOrder = tags.value.size
                            )
                        )
                    } else {
                        repository.update(
                            existing.copy(name = normalized, color = color.uppercase())
                        )
                    }
                }.onSuccess {
                    _error.value = null
                    onSaved()
                }.onFailure {
                    _error.value = it.message?.takeIf(String::isNotBlank)
                        ?: context.getString(R.string.tag_manager_error_save_failed)
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun delete(tag: Tag) {
        viewModelScope.launch { repository.delete(tag) }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        val current = tags.value
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
            return
        }
        val reordered = current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }.mapIndexed { index, tag -> tag.copy(sortOrder = index) }
        viewModelScope.launch { repository.updateAll(reordered) }
    }

    fun reorder(ordered: List<Tag>) {
        val normalized = ordered.mapIndexed { index, tag ->
            tag.copy(sortOrder = index)
        }
        viewModelScope.launch { repository.updateAll(normalized) }
    }
}
