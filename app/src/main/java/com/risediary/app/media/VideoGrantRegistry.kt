package com.risediary.app.media

import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes grant ownership with record writes and maintenance rollback. */
@Singleton
class VideoGrantRegistry internal constructor(
    private val recordUris: suspend () -> Set<String>,
    private val access: VideoFileAccess,
    private val gate: DataMaintenanceGate,
    private val scope: CoroutineScope
) {
    @Inject constructor(database: AppDatabase, access: VideoFileAccess, gate: DataMaintenanceGate,
        timerStore: com.risediary.app.service.TimerSessionStore,
        forms: com.risediary.app.ui.form.RecordFormSessionStore) :
        this({
            database.flightDao().getVideoUris().toSet() +
                forms.videoUris() + listOfNotNull(timerStore.load().video?.video?.uriString)
        }, access, gate, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val mutex = Mutex()
    private val owners = mutableMapOf<String, Set<String>>()

    init {
        scope.launch {
            gate.state.collect { if (it == DataMaintenanceGate.State.IDLE) cleanupSafely() }
        }
    }

    suspend fun retain(owner: String, uris: Set<String>) {
        mutex.withLock { owners[owner] = uris }
    }

    suspend fun acquire(owner: String, uri: String, flags: Int, currentUris: Set<String>): Result<LocalVideoRef> =
        gate.write {
            mutex.withLock {
                // Pin before acquisition, closing the gap between permission and form state.
                owners[owner] = currentUris + uri
                try {
                    access.acquire(uri, flags).also { result ->
                        owners[owner] = if (result.isSuccess) currentUris + uri else currentUris
                    }
                } catch (error: Exception) {
                    owners[owner] = currentUris
                    throw error
                }
            }
        }

    fun forget(owner: String) {
        scope.launch {
            mutex.withLock { owners.remove(owner) }
            cleanupSafely()
        }
    }

    fun requestCleanup() { scope.launch { cleanupSafely() } }

    private suspend fun cleanupSafely() {
        if (gate.state.value != DataMaintenanceGate.State.IDLE) return
        try {
            gate.write {
                mutex.withLock {
                    access.releaseUnused(recordUris() + owners.values.flatten())
                }
            }
        } catch (_: DataMaintenanceBusyException) {
            // The next IDLE transition retries against the final database, after rollback if needed.
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep grants on any database/ledger failure; never guess that references are empty.
        }
    }
}
