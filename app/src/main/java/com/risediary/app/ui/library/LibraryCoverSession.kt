package com.risediary.app.ui.library

import com.risediary.app.data.library.LibraryCoverFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LibraryCoverEditState(val assetId: String? = null, val frame: LibraryCoverFrame? = null,
    val positionMillis: Long = 0L, val loading: Boolean = false, val saving: Boolean = false,
    val done: Boolean = false, val error: String? = null)

/** Generation checks protect against native reads which finish after cancellation. */
internal class LibraryCoverSession(private val scope: CoroutineScope,
    private val preview: suspend (String, Long?) -> LibraryCoverFrame,
    private val persist: suspend (String, LibraryCoverFrame) -> Unit) {
    private val mutable = MutableStateFlow(LibraryCoverEditState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var requested: Long? = null
    fun open(id: String, position: Long?) {
        if (state.value.saving) return
        job?.cancel(); generation++
        mutable.value = LibraryCoverEditState(assetId = id)
        request(position, false)
    }
    fun seek(position: Long) {
        val last = state.value.frame?.durationMillis?.let { (it - 1L).coerceAtLeast(0L) } ?: Long.MAX_VALUE / 1000L
        request(position.coerceIn(0L, last), true)
    }
    fun restoreAutomatic() = request(null, false)
    fun retry() = request(requested, false)
    private fun request(position: Long?, delayed: Boolean) {
        val id = state.value.assetId ?: return
        if (state.value.saving || state.value.done) return
        requested = position
        val token = ++generation
        job?.cancel()
        mutable.update { it.copy(loading = true, positionMillis = position ?: it.positionMillis, error = null) }
        job = scope.launch {
            try {
                if (delayed) delay(180L)
                val frame = preview(id, position)
                if (token == generation) mutable.update { it.copy(frame = frame, positionMillis = frame.positionMillis, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) mutable.update { it.copy(loading = false, error = "无法生成封面，请换个位置或重试") } }
        }
    }
    fun save() {
        val current = state.value
        val id = current.assetId ?: return
        val frame = current.frame ?: return
        if (current.loading || current.saving || current.done || frame.positionMillis != current.positionMillis) return
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try { persist(id, frame); mutable.update { it.copy(saving = false, done = true) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(saving = false, error = "封面未能保存，请重试") } }
        }
    }
    fun close() { job?.cancel(); generation++; mutable.value = LibraryCoverEditState() }
}
