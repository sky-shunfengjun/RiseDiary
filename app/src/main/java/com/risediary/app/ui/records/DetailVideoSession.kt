package com.risediary.app.ui.records

import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoAccessState
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.media.VideoPlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class DetailVideoState(
    val recordId: Long? = null,
    val video: LocalVideoRef? = null,
    val settingsReady: Boolean = false,
    val hidden: Boolean = true,
    val loading: Boolean = false,
    val prepared: Boolean = false,
    val problem: String? = null,
    val settingsFailed: Boolean = false
) {
    val controlsEnabled: Boolean get() = settingsReady && !hidden && prepared && !loading && problem == null
}

/** Per-detail memory only. Native player calls and all state changes run on the owning main scope. */
internal class DetailVideoSession(
    private val scope: CoroutineScope,
    private val settings: Flow<Boolean>,
    private val checkAccess: suspend (LocalVideoRef) -> VideoAccessState,
    private val controller: VideoPlayerController
) {
    private val mutableState = MutableStateFlow(DetailVideoState())
    val state = mutableState.asStateFlow()
    private var hiddenByDefault = false
    private var settingsJob: Job? = null
    private var loadJob: Job? = null
    private var epoch = 0L
    // A previous record may use the same URI; its position must not carry into the new record.
    private var loadedRecordId: Long? = null

    init {
        retrySettings()
        scope.launch {
            controller.error.collect { error ->
                val current = state.value
                if (error != null && current.prepared && controller.playback.value?.video == current.video) {
                    controller.pause()
                    mutableState.value = current.copy(problem = error, loading = false)
                }
            }
        }
    }

    fun bind(recordId: Long, video: LocalVideoRef?) {
        val current = state.value
        if (current.recordId == recordId && current.video == video) return
        cancelLoad()
        controller.pause()
        loadedRecordId = null
        mutableState.value = current.copy(recordId = recordId, video = video, hidden = !current.settingsReady || hiddenByDefault,
            prepared = false, loading = false, problem = if (current.settingsFailed) current.problem else null)
        if (state.value.settingsReady && !state.value.hidden) load()
    }

    fun show() {
        if (!state.value.settingsReady || state.value.video == null) return
        mutableState.value = state.value.copy(hidden = false)
        if (!state.value.prepared || state.value.problem != null || controller.error.value != null) load()
    }

    fun hide() {
        controller.pause()
        cancelLoad()
        mutableState.value = state.value.copy(hidden = true, loading = false)
    }

    fun onBackground() {
        controller.pause()
        if (hiddenByDefault || !state.value.settingsReady) hide()
    }

    fun retry() {
        if (state.value.settingsFailed || !state.value.settingsReady) retrySettings()
        else if (!state.value.hidden) load()
    }

    private fun retrySettings() {
        settingsJob?.cancel()
        controller.pause()
        cancelLoad()
        mutableState.value = state.value.copy(settingsReady = false, hidden = true, loading = false,
            problem = null, settingsFailed = false)
        settingsJob = scope.launch {
            try {
                settings.distinctUntilChanged().collect { hidden ->
                    val firstRead = !state.value.settingsReady
                    val newlyHidden = hidden && !hiddenByDefault
                    hiddenByDefault = hidden
                    mutableState.value = state.value.copy(settingsReady = true, settingsFailed = false,
                        hidden = if (firstRead) hidden else state.value.hidden, problem = null)
                    if (newlyHidden) hide()
                    if (!state.value.hidden && !state.value.prepared) load()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                hide()
                mutableState.value = state.value.copy(settingsReady = false, settingsFailed = true,
                    problem = "无法读取视频隐藏设置，请重试")
            }
        }
    }

    private fun cancelLoad() { epoch++; loadJob?.cancel(); loadJob = null }

    private fun load() {
        val target = state.value
        val video = target.video ?: return
        if (!target.settingsReady || target.hidden) return
        cancelLoad()
        controller.pause()
        val request = epoch
        mutableState.value = target.copy(loading = true, problem = null)
        loadJob = scope.launch {
            try {
                val access = checkAccess(video)
                if (request != epoch || state.value.hidden || state.value.video != video) return@launch
                if (access != VideoAccessState.READABLE) {
                    mutableState.value = state.value.copy(loading = false, problem = "视频无法访问，请重试或重新关联")
                    return@launch
                }
                val previous = controller.playback.value?.takeIf { loadedRecordId == target.recordId && it.video == video }
                controller.load(previous ?: VideoPlaybackSnapshot(video))
                loadedRecordId = target.recordId
                mutableState.value = state.value.copy(loading = false, prepared = true, problem = controller.error.value)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == epoch) mutableState.value = state.value.copy(loading = false,
                    problem = "视频读取失败，请重试或重新关联")
            }
        }
    }
}
