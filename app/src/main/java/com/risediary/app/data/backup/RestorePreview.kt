package com.risediary.app.data.backup

import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord

enum class RestoreMode { MERGE, REPLACE }
data class RestoreCounts(val added: Int = 0, val updated: Int = 0, val skipped: Int = 0,
    val current: Int = 0, val backup: Int = 0, val final: Int = 0)
data class RestorePreview(val preparationId: String, val revision: Long, val mode: RestoreMode,
    val flights: RestoreCounts, val lengths: RestoreCounts, val settingsChanges: List<String>)
sealed interface RestoreConfirmation {
    data class Finished(val result: BackupResult) : RestoreConfirmation
    data class Changed(val preview: RestorePreview) : RestoreConfirmation
}

internal object RestoreIdentityPolicy {
    fun mergeFlight(backup: Flight, local: Flight?): Flight = if (local == null) backup else backup.copy(
        id = local.id, globalId = local.globalId, recordDraftId = local.recordDraftId)
    fun mergeLength(backup: LengthRecord, local: LengthRecord?): LengthRecord =
        if (local == null) backup else backup.copy(id = local.id, globalId = local.globalId)
}

/** Counts describe final target rows, after all legacy backup duplicates have been applied. */
internal fun restoreRecordAction(originalPayload: String?,finalPayload: String): String = when {
    originalPayload == null -> "added"
    originalPayload == finalPayload -> "skipped"
    else -> "updated"
}
