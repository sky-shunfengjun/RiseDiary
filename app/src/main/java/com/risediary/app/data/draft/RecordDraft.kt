package com.risediary.app.data.draft

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.ui.form.QuantityDraftSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class RecordDraftSnapshot(
    val draftId: String,
    val revision: Long = 0L,
    val sessionId: String? = null,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val timingSource: String = "manual",
    val timeWasEdited: Boolean = false,
    val quantity: QuantityDraftSnapshot,
    val video: LocalVideoRef? = null,
    val distanceText: String = "",
    val methodTags: List<String> = emptyList(),
    val moodNote: String = ""
)

@Entity(tableName = "record_drafts", indices = [Index(value = ["activeSlot"], unique = true)])
data class RecordDraftEntity(
    @PrimaryKey val draftId: String,
    val activeSlot: Int?,
    val revision: Long,
    val payload: String,
    val completedFlightId: Long?
)

class RecordDraftConflictException : IllegalStateException("草稿已变化，请重新打开")
class DraftAlreadyCommittedException(val flightId: Long) : IllegalStateException("记录已保存")
