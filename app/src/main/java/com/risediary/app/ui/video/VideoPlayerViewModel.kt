package com.risediary.app.ui.video

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.Media3VideoPlayerController
import com.risediary.app.media.VideoAccessState
import com.risediary.app.media.VideoFileAccess
import com.risediary.app.media.VideoGrantRegistry
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.media.localVideoRef
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val access: VideoFileAccess,
    private val grants: VideoGrantRegistry,
    private val flights: FlightRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val controller = Media3VideoPlayerController(context)
    private val owner = "player:" + UUID.randomUUID()
    private var opening: Job? = null
    private var requested: LocalVideoRef? = null
    private var recordId: Long? = null
    private var loadedUri: String? = null
    private val loadingState = MutableStateFlow(true)
    val loading = loadingState.asStateFlow()
    private val accessFailure = MutableStateFlow<String?>(null)
    val error = accessFailure.asStateFlow()

    init {
        viewModelScope.launch {
            controller.playback.collect { value ->
                if (value != null) {
                    savedState["uri"] = value.video.uriString
                    savedState["position"] = value.positionMillis
                    savedState["speed"] = value.speed
                    savedState["loop"] = value.loop
                }
            }
        }
    }

    fun open(video: LocalVideoRef) {
        if (requested == video) return
        requested = video
        load(video)
    }
    fun openRecord(id: Long) {
        if (requested != null || opening?.isActive == true) return
        recordId = id
        opening = viewModelScope.launch {
            try {
                val video = flights.getById(id)?.localVideoRef()
                if (video == null) {
                    loadingState.value = false
                    accessFailure.value = "这条记录没有视频"
                } else {
                    requested = video
                    load(video)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                loadingState.value = false
                accessFailure.value = "无法读取视频关联，请返回后重试"
            }
        }
    }

    private fun load(video: LocalVideoRef) {
        // Do not cancel the record lookup coroutine from inside itself.
        viewModelScope.launch {
            loadingState.value = true
            accessFailure.value = null
            try {
                grants.retain(owner, setOf(video.uriString))
                val state = access.check(video)
                if (state != VideoAccessState.READABLE) {
                    controller.pause()
                    accessFailure.value = if (recordId != null) "视频无法访问，请返回记录详情重新关联" else when (state) {
                        VideoAccessState.MISSING -> "原视频已删除或移动，请在编辑记录中更换"
                        VideoAccessState.PERMISSION_LOST -> "无法访问原视频，请在编辑记录中重新选择"
                        else -> "视频关联无效，请在编辑记录中更换"
                    }
                } else if (loadedUri != video.uriString || controller.error.value != null) {
                    val sameVideo = savedState.get<String>("uri") == video.uriString
                    controller.load(VideoPlaybackSnapshot(video,
                        if (sameVideo) savedState.get<Long>("position") ?: 0L else 0L,
                        if (sameVideo) savedState.get<Float>("speed") ?: 1f else 1f,
                        sameVideo && savedState.get<Boolean>("loop") == true))
                    loadedUri = video.uriString
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                controller.pause()
                accessFailure.value = "无法打开视频，请重试"
            } finally { loadingState.value = false }
        }
    }

    fun retry() {
        if (loadingState.value && opening?.isActive == true) return
        requested?.let(::load) ?: recordId?.let(::openRecord)
    }
    fun pause() = controller.pause()
    override fun onCleared() {
        controller.release()
        grants.forget(owner)
    }
}
