package com.risediary.app.data

/** The shared edit timestamp boundary for forms, video relinking and backup export. */
internal object RecordTimestamps {
    fun updatedAt(createdAt: Long, previousUpdatedAt: Long, now: Long): Long = maxOf(createdAt, previousUpdatedAt, now)
}
