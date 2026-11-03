package com.risediary.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerStateHolder @Inject constructor(
    private val usage: com.risediary.app.data.library.LibraryUsageRegistry
) {
    constructor() : this(com.risediary.app.data.library.LibraryUsageRegistry())
    private val stateLock = Any()
    private var generation = 0L
    private val mutableState = MutableStateFlow(TimerSession())
    val state: StateFlow<TimerSession> = mutableState.asStateFlow()

    private val mutablePersistenceError = MutableStateFlow(false)
    val persistenceError: StateFlow<Boolean> = mutablePersistenceError.asStateFlow()

    private val mutableCommandError = MutableStateFlow<String?>(null)
    val commandError: StateFlow<String?> = mutableCommandError.asStateFlow()
    fun setCommandError(message: String?) { mutableCommandError.value = message }
    fun setPersistenceError(failed: Boolean) { mutablePersistenceError.value = failed }

    fun set(session: TimerSession) {
        synchronized(stateLock) {
            protect(session)
            // Count actions even when StateFlow suppresses an equal value, including an idle reset.
            generation++
            mutableState.value = session
        }
    }

    /** A suspended disk read may only restore the idle state that originally requested it. */
    internal suspend fun restoreIfIdle(load: suspend () -> TimerSession): TimerSession {
        val (current, expectedGeneration) = synchronized(stateLock) {
            mutableState.value to generation
        }
        if (current.status != TimerStatus.IDLE) return current
        val persisted = load()
        return synchronized(stateLock) {
            if (generation == expectedGeneration) {
                protect(persisted)
                generation++
                mutableState.value = persisted
            }
            mutableState.value
        }
    }

    private fun protect(session: TimerSession) {
        val material = session.videoSource as? com.risediary.app.media.VideoSourceRef.LibraryVideo
        if (material != null && session.status != TimerStatus.IDLE) check(usage.hold(this, material.materialId)) { "素材正在删除" }
        else usage.release(this)
    }
}
