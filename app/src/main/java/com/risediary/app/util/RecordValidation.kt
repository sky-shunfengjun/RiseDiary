package com.risediary.app.util

object RecordValidation {
    const val MAX_DURATION_SECONDS = 120 * 60
    const val LEGACY_MAX_DURATION_SECONDS = 24 * 60 * 60
    const val MAX_DIRECT_SPURTS = 1_000
    const val MAX_DIRECT_VOLUME_ML = 1_000f
    const val MAX_STORED_SPURTS = 10_000
    const val MAX_STORED_VOLUME_ML = 100_000f
    const val MAX_NEW_NOTE_LENGTH = 10_000

    /** Storage compatibility is wider than the bounds for a directly entered unit. */
    fun validateStoredQuantity(spurtCount: Int?, volumeMl: Float?): String? {
        if (spurtCount == null && volumeMl == null) return "请填写射精量（股数或毫升）"
        if (spurtCount != null && spurtCount !in 1..MAX_STORED_SPURTS) {
            return "保存的股数超出有效范围"
        }
        if (volumeMl != null && (!volumeMl.isFinite() || volumeMl <= 0f || volumeMl > MAX_STORED_VOLUME_ML)) {
            return "保存的毫升数超出有效范围"
        }
        return null
    }

    /** Existing long notes are preserved and can be shortened without truncation. */
    fun validateNote(note: String, existingNote: String? = null): String? =
        if (note.length <= MAX_NEW_NOTE_LENGTH ||
            (existingNote != null && existingNote.length > MAX_NEW_NOTE_LENGTH && note.length <= existingNote.length)
        ) null else "新备注最多可以填写 10000 字"

    fun validate(
        durationSeconds: Int,
        spurtCount: Int?,
        volumeMl: Float?,
        distanceCm: Float?,
        distanceWasEntered: Boolean,
        allowLegacyDuration: Boolean = false
    ): String? {
        val durationLimit = if (allowLegacyDuration) LEGACY_MAX_DURATION_SECONDS else MAX_DURATION_SECONDS
        if (durationSeconds !in 1..durationLimit) {
            return "起飞用时需要在 1 秒到 120 分钟之间"
        }
        validateStoredQuantity(spurtCount, volumeMl)?.let { return it }
        if (distanceWasEntered && (distanceCm == null || !distanceCm.isFinite() || distanceCm !in 0f..1_000f)) {
            return "距离需要在 0 到 1000 厘米之间"
        }
        return null
    }
}