package com.risediary.app.ui.form

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.DataMaintenanceBusyException
import kotlinx.coroutines.CancellationException

internal data class FormRecordSaveResult(
    val flight: Flight,
    val achievementKeys: List<String>,
    val followUpFailures: List<Exception>
)

internal class StaleRecordDraftException : IllegalStateException("The stored record changed after this draft was loaded")

/** Caller holds the write permit across reading, comparing and committing. */
internal class FormRecordSaveWorkflow(
    private val insert: suspend (Flight) -> Flight,
    private val update: suspend (Flight) -> Unit,
    private val readCurrent: suspend (Long) -> Flight?
) {
    var persistedFlight: Flight? = null
        private set

    fun loadOriginal(flight: Flight) {
        persistedFlight = flight
    }

    suspend fun save(
        flight: Flight,
        afterSave: suspend (Flight) -> List<String> = { emptyList() },
        followUps: List<suspend () -> Unit> = emptyList()
    ): FormRecordSaveResult {
        val existing = persistedFlight
        if (existing != null && readCurrent(existing.id) != existing) {
            throw StaleRecordDraftException()
        }
        val stored = if (existing == null) {
            insert(flight)
        } else {
            flight.copy(
                id = existing.id,
                globalId = existing.globalId,
                recordSource = existing.recordSource,
                sourceDeviceId = existing.sourceDeviceId
            ).also { update(it) }
        }
        persistedFlight = stored
        val failures = mutableListOf<Exception>()
        val keys = run {
            try {
                afterSave(stored)
            } catch (error: DataMaintenanceBusyException) {
                failures += error
                emptyList()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
                emptyList()
            }
        }
        followUps.forEach { work ->
            try {
                work()
            } catch (error: DataMaintenanceBusyException) {
                failures += error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
            }
        }
        return FormRecordSaveResult(stored, keys, failures)
    }
}
