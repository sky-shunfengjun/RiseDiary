package com.risediary.app.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Runs before queries are admitted. Saved records and completed submission IDs are untouched. */
object UnsubmittedFormCleanup : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM record_drafts WHERE activeSlot = 1")
    }
}
