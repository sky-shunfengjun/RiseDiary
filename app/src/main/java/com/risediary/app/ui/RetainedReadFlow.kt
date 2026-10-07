package com.risediary.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** An IO failure is separate from data; it never replaces the last successful snapshot. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class RetainedReadFlow<T>(scope: CoroutineScope, source: Flow<T>, initial: T) {
    private val requests = MutableStateFlow(0L)
    private val current = MutableStateFlow(initial)
    val data: StateFlow<T> = current.asStateFlow()
    private val failure = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = failure.asStateFlow()
    init {
        scope.launch {
            requests.flatMapLatest { source.onEach { failure.value = false }.catch { failure.value = true } }.collect {
                current.value = it
            }
        }
    }
    fun retry() { requests.value++ }
}
