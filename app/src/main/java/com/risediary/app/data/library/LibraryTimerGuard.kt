package com.risediary.app.data.library

import com.risediary.app.service.TimerSessionStore
import com.risediary.app.service.TimerStateHolder
import javax.inject.Inject

/** Deletion must see a persisted timer even if its app-level restoration has not finished yet. */
class LibraryTimerGuard @Inject constructor(private val holder: TimerStateHolder, private val store: TimerSessionStore) {
    suspend fun restore() { holder.restoreIfIdle(store::load) }
}
