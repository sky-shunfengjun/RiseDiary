package com.risediary.app.ui.settings

import com.risediary.app.data.DataMaintenanceBusyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class DetailVideoSettingsUiState(
    val hiddenByDefault: Boolean = false,
    val ready: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

/** Committed values drive the toggle; a failed write retains its choice for the same retry button. */
internal class DetailVideoSettingsEditor(
    private val scope: CoroutineScope,
    private val values: Flow<Boolean>,
    private val write: suspend (Boolean) -> Unit,
    private val readError: String,
    private val writeError: String
) {
    private val mutableState = MutableStateFlow(DetailVideoSettingsUiState())
    val state = mutableState.asStateFlow()
    private var readJob: Job? = null
    private var pending: Boolean? = null

    init { read() }

    fun setHidden(hidden: Boolean) {
        if (!state.value.ready || state.value.saving) return
        pending = hidden
        mutableState.value = state.value.copy(saving = true, error = null)
        scope.launch {
            try {
                write(hidden)
                pending = null
                mutableState.value = state.value.copy(saving = false, error = null)
                if (!state.value.ready) read()
            } catch (_: DataMaintenanceBusyException) {
                mutableState.value = state.value.copy(error = writeError)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = state.value.copy(error = writeError) }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }

    fun retry() {
        if (state.value.saving) return
        val target = pending
        if (state.value.ready && target != null) setHidden(target) else read()
    }

    private fun read() {
        readJob?.cancel()
        mutableState.value = state.value.copy(ready = false, error = null)
        readJob = scope.launch {
            var firstValue = true
            try {
                values.collect { hidden ->
                    mutableState.value = state.value.copy(hiddenByDefault = hidden, ready = true,
                        error = if (pending == null) null else state.value.error)
                    if (firstValue) {
                        firstValue = false
                        if (!state.value.saving) pending?.let(::setHidden)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = state.value.copy(ready = false, error = readError) }
        }
    }
}
