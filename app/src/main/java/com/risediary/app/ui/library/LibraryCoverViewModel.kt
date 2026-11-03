package com.risediary.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.library.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LibraryCoverViewModel @Inject constructor(manager: LibraryCoverManager, private val usage: LibraryUsageRegistry) : ViewModel() {
    internal val editor = LibraryCoverSession(viewModelScope, manager::preview, manager::save)
    val state = editor.state
    private val owner = Any()
    private val problem = MutableStateFlow<String?>(null)
    val error = problem.asStateFlow()
    fun open(asset: LibraryAsset) {
        if (state.value.saving) return
        usage.release(owner)
        if (!usage.hold(owner, asset.id)) { problem.value = "素材正在删除，请返回重试"; return }
        problem.value = null
        editor.open(asset.id, asset.coverFrameMillis)
    }
    fun close() { if (!state.value.saving) { editor.close(); problem.value = null; usage.release(owner) } }
    override fun onCleared() { editor.close(); usage.release(owner) }
}
