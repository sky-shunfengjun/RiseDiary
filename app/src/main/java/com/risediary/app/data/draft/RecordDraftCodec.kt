package com.risediary.app.data.draft

import com.risediary.app.media.validateLocalVideoFields
import com.risediary.app.ui.form.RecordFormQuantityDraft
import com.risediary.app.util.RecordValidation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RecordDraftCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(value: RecordDraftSnapshot): String {
        validate(value)
        return json.encodeToString(value)
    }
    fun decode(payload: String): RecordDraftSnapshot {
        require(payload.length <= 200_000) { "草稿内容无效" }
        return json.decodeFromString<RecordDraftSnapshot>(payload).also(::validate)
    }
    private fun validate(value: RecordDraftSnapshot) {
        require(value.draftId.isNotBlank() && value.draftId.length <= 128 && value.revision >= 0L)
        require(value.sessionId == null || (value.sessionId.isNotBlank() && value.sessionId.length <= 128))
        require(value.startTime > 0L && value.endTime >= value.startTime)
        require(value.durationSeconds in 0..RecordValidation.MAX_DURATION_SECONDS)
        require(value.timingSource in listOf("manual", "timer"))
        require(value.quantity.manualText.length <= 7 && value.quantity.manualText.matches(Regex("""\d{0,4}(\.\d{0,2})?""")))
        RecordFormQuantityDraft.restore(value.quantity)
        require(value.distanceText.length <= 7 && value.distanceText.matches(Regex("""\d{0,4}(\.\d{0,2})?""")) && value.moodNote.length <= RecordValidation.MAX_NEW_NOTE_LENGTH)
        require(value.methodTags.size <= 100 && value.methodTags.all { it.trim().length in 1..20 })
        require(validateLocalVideoFields(value.video?.uriString, value.video?.displayName, value.video?.mimeType) == null)
    }
}
