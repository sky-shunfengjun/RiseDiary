package com.risediary.app.ui.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.media.*
import com.risediary.app.service.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class VideoTimerViewModel internal constructor(
    val controller: VideoPlayerController,
    private val access: VideoFileAccess,
    private val grants: VideoGrantRegistry,
    private val timer: TimerController,
    private val readTimer: suspend () -> TimerSession,
    private val holder: TimerStateHolder,
    private val wallClock: Clock,
    private val elapsedClock: ElapsedRealtimeClock,
    private val savedState: SavedStateHandle
) : ViewModel() {
    @Inject constructor(
        @ApplicationContext context: Context,
        access: VideoFileAccess,
        grants: VideoGrantRegistry,
        timer: TimerController,
        timerStore: TimerSessionStore,
        holder: TimerStateHolder,
        wallClock: Clock,
        elapsedClock: ElapsedRealtimeClock,
        savedState: SavedStateHandle
    ) : this(Media3VideoPlayerController(context), access, grants, timer,
        timerStore::load, holder, wallClock, elapsedClock, savedState)
    val session = timer.state
    private val loadingState = MutableStateFlow(true)
    val loading = loadingState.asStateFlow()
    private val errorState = MutableStateFlow<String?>(null)
    val error = errorState.asStateFlow()
    private val noticeState = MutableStateFlow<String?>(null)
    val notice = noticeState.asStateFlow()
    private val startedState = MutableStateFlow(false)
    val started = startedState.asStateFlow()
    private val startingState = MutableStateFlow(false)
    val starting = startingState.asStateFlow()
    private val gate = VideoTimerStartGate()
    private val owner = "video-timer:" + UUID.randomUUID()
    private var sessionId: String? = null
    private var ready = false
    private val selectingState = MutableStateFlow(false)
    val selecting = selectingState.asStateFlow()
    private var lastSnapshot: VideoPlaybackSnapshot? = null
    private var lastSent: VideoPlaybackSnapshot? = null
    private var lastCheckpoint = 0L
    private var lastPlaying = false
    private var resumingFromPlayback = false
    private val json = Json { encodeDefaults = true }

    init {
        viewModelScope.launch {
            controller.interactions.collect { snapshot ->
                lastSnapshot = snapshot
                if (ready && startedState.value) savePlayback(true)
            }
        }
        viewModelScope.launch {
            controller.playback.collect { snapshot ->
                if (snapshot != null) {
                    lastSnapshot = snapshot
                    savedState["prepared_playback"] = json.encodeToString(snapshot)
                    val now = elapsedClock.millis()
                    if (ready && startedState.value && now - lastCheckpoint >= TIMER_CHECKPOINT_MILLIS)
                        savePlayback(false)
                }
            }
        }
        viewModelScope.launch {
            controller.isPlaying.collect { playing ->
                val current = timer.state.value
                if (playing && ready && startedState.value && current.sessionId == sessionId &&
                    current.status == TimerStatus.PAUSED && current.finishCandidate == null) {
                    resumeFromPlayback()
                } else if (playing && (!ready || (startedState.value &&
                        (current.sessionId != sessionId || current.status != TimerStatus.RUNNING || current.finishCandidate != null)))) {
                    controller.pause()
                } else if (gate.onPlayback(playing)) requestStart()
                if (!playing && lastPlaying) savePlayback(true)
                lastPlaying = playing
            }
        }
        viewModelScope.launch {
            timer.state.collect { current ->
                if (current.sessionId == sessionId) {
                    if (current.status == TimerStatus.RUNNING) noticeState.value = null
                    if (current.status != TimerStatus.IDLE) {
                        startedState.value = true
                        gate.acknowledgeStarted()
                    }
                    if (current.finishCandidate != null || (current.status != TimerStatus.RUNNING &&
                            !(current.status == TimerStatus.PAUSED && resumingFromPlayback))) pause()
                }
            }
        }
    }

    private fun resumeFromPlayback() {
        val id = sessionId ?: return
        if (resumingFromPlayback) return
        if (holder.persistenceError.value) {
            controller.pause()
            noticeState.value = "请先重试保存计时"
            return
        }
        resumingFromPlayback = true
        timer.resume(id)
        viewModelScope.launch {
            try {
                val outcome = withTimeout(6_000L) {
                    combine(timer.state, holder.persistenceError, holder.commandError) { state, failed, message ->
                        Triple(state, failed, message)
                    }.first { (state, failed, message) ->
                        state.sessionId != id || state.status == TimerStatus.RUNNING || failed || message != null
                    }
                }
                check(outcome.first.sessionId == id && outcome.first.status == TimerStatus.RUNNING)
                noticeState.value = null
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                controller.pause()
                noticeState.value = "计时未能继续，请重试"
            } catch (_: Exception) {
                controller.pause()
                noticeState.value = "计时未能继续，请重试"
            } finally { resumingFromPlayback = false }
        }
    }

    fun open(id: String) {
        if (sessionId != null) return
        sessionId = id
        initialize(id)
    }

    /** Initialization failures must restore the session and retry its original video again. */
    private fun initialize(id: String) {
        loadingState.value = true
        errorState.value = null
        viewModelScope.launch {
            try {
                val current = holder.restoreIfIdle(readTimer)
                if (current.status != TimerStatus.IDLE && current.sessionId != id) {
                    errorState.value = "已有其他计时，请返回处理"
                    return@launch
                }
                if (current.sessionId == id && current.status != TimerStatus.IDLE) {
                    startedState.value = true
                    gate.acknowledgeStarted()
                    current.video?.let { load(it) }
                    if (current.isActive) timer.restore()
                } else {
                    savedState.get<String>("prepared_playback")?.let { load(json.decodeFromString(it)) }
                }
                ready = true
            } catch (_: DataMaintenanceBusyException) { errorState.value = "数据处理中，请稍后再试" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { errorState.value = "无法读取计时或视频，请重试" }
            finally { loadingState.value = false }
        }
    }

    fun selectVideo(uri: Uri, flags: Int) {
        if (!ready || selectingState.value || startedState.value) return
        selectingState.value = true
        loadingState.value = true
        viewModelScope.launch {
            try {
                val video = grants.acquire(owner, uri.toString(), flags,
                    listOfNotNull(lastSnapshot?.video?.uriString).toSet()).getOrThrow()
                load(VideoPlaybackSnapshot(video))
                errorState.value = null
                grants.retain(owner, setOf(video.uriString))
                grants.requestCleanup()
            } catch (_: DataMaintenanceBusyException) { errorState.value = "数据处理中，请稍后再试" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { errorState.value = "无法读取这个视频，请重新选择" }
            finally { selectingState.value = false; loadingState.value = false }
        }
    }

    private suspend fun load(snapshot: VideoPlaybackSnapshot) {
        grants.retain(owner, setOf(snapshot.video.uriString))
        check(access.check(snapshot.video) == VideoAccessState.READABLE) { "无法访问原视频" }
        controller.load(snapshot)
        lastSnapshot = snapshot
        lastSent = snapshot
        lastCheckpoint = elapsedClock.millis()
    }

    private fun requestStart() {
        val id = sessionId ?: return
        val snapshot = controller.playback.value ?: return
        val wall = wallClock.millis()
        val mono = elapsedClock.millis()
        startingState.value = true
        viewModelScope.launch {
            try {
                timer.start(TimerStartRequest(id, TimerKind.VIDEO, snapshot, wall, mono))
                val outcome = withTimeout(6_000L) {
                    combine(timer.state, holder.persistenceError, holder.commandError) { state, failed, message ->
                        Triple(state, failed, message)
                    }.first { (state, failed, message) -> state.sessionId == id || failed || message != null }
                }
                check(outcome.first.sessionId == id && outcome.first.status != TimerStatus.IDLE)
                gate.acknowledgeStarted()
                startedState.value = true
                errorState.value = null
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException && cancelled !is DataMaintenanceBusyException) throw cancelled
                controller.pause()
                errorState.value = "计时未能开始，请重试"
            } catch (_: Exception) {
                controller.pause()
                errorState.value = "计时未能开始，请先处理当前计时后重试"
            } finally { startingState.value = false }
        }
    }

    private fun savePlayback(immediate: Boolean) {
        val id = sessionId ?: return
        val snapshot = controller.playback.value ?: lastSnapshot ?: return
        if (!startedState.value || timer.state.value.sessionId != id || !timer.state.value.isActive) return
        lastSent = snapshot
        lastCheckpoint = elapsedClock.millis()
        timer.updatePlayback(id, snapshot, immediate)
    }

    fun pause() {
        controller.pause()
        savePlayback(true)
    }

    fun retry() {
        if (loadingState.value) return
        if (!ready) {
            initialize(sessionId ?: return)
            return
        }
        loadingState.value = true
        viewModelScope.launch {
            try {
                if (gate.state == VideoStartState.REQUESTED && !startedState.value) {
                    timer.retryPersistence()
                    withTimeout(6_000L) {
                        combine(timer.state, holder.persistenceError) { state, failed -> state to failed }
                            .first { (state, failed) -> state.sessionId == sessionId || !failed }
                    }
                    if (timer.state.value.sessionId == sessionId) {
                        gate.acknowledgeStarted()
                        startedState.value = true
                    } else gate.retryAfterStartFailure()
                }
                errorState.value = null
                lastSnapshot?.let { load(it) }
            } catch (cancelled: CancellationException) {
                if (cancelled !is TimeoutCancellationException) throw cancelled
                errorState.value = "无法重试，请返回后再试"
            } catch (_: Exception) { errorState.value = "视频无法访问，请返回后处理计时" }
            finally { loadingState.value = false }
        }
    }

    override fun onCleared() {
        controller.release()
        if (gate.state != VideoStartState.REQUESTED || startedState.value) grants.forget(owner)
    }
}
