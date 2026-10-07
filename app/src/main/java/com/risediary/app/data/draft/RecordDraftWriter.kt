package com.risediary.app.data.draft

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces rapid changes; revisions are read after the previous write finishes. */
internal class RecordDraftWriter(
    private val repository: RecordDraftRepository,
    initial: RecordDraftSnapshot,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val guard = Any()
    private val writes = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var latest = initial
    private var pending = false
    private val storedState = MutableStateFlow(initial)
    val stored = storedState.asStateFlow()
    private val failedState = MutableStateFlow<Throwable?>(null)
    val failure = failedState.asStateFlow()
    private val worker = scope.launch {
        for (ignored in signal) flush()
    }

    fun update(snapshot: RecordDraftSnapshot) {
        synchronized(guard) {
            check(snapshot.draftId == latest.draftId)
            latest = snapshot
            pending = true
        }
        signal.trySend(Unit)
    }

    suspend fun flush(): Result<RecordDraftSnapshot> = writes.withLock {
        while (true) {
            val snapshot = synchronized(guard) { if (pending) latest else null }
                ?: return@withLock Result.success(storedState.value)
            val result = repository.save(snapshot, storedState.value.revision)
            val saved = result.getOrElse {
                failedState.value = it
                return@withLock Result.failure(it)
            }
            storedState.value = saved
            failedState.value = null
            synchronized(guard) { if (latest === snapshot) pending = false }
        }
        @Suppress("UNREACHABLE_CODE")
        Result.success(storedState.value)
    }

    /** Queued writes outlive a popped page; the owner can release its temporary video pin afterward. */
    fun close(onFinished: () -> Unit = {}) {
        signal.trySend(Unit)
        signal.close()
        worker.invokeOnCompletion { if (failedState.value == null) onFinished() }
    }
}
