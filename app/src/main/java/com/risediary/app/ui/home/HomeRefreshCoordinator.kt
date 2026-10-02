package com.risediary.app.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread requests coalesce, retaining one pending refresh while a query is running. */
internal class HomeRefreshCoordinator<T>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
    private val publish: (T) -> Unit
) {
    private var revision = 0L
    private var job: Job? = null
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun request(silent: Boolean) {
        revision++
        if (!silent) _isRefreshing.value = true
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                do {
                    val requestedRevision = revision
                    val result = try {
                        Result.success(load())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    if (requestedRevision == revision) result.onSuccess(publish)
                } while (requestedRevision != revision)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
