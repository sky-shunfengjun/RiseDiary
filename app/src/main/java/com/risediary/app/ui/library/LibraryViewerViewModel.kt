package com.risediary.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.BuildConfig
import com.risediary.app.data.library.LibraryAsset
import com.risediary.app.data.library.LibraryRepository
import com.risediary.app.data.library.LibraryTree
import com.risediary.app.data.library.LibraryUsageRegistry
import com.risediary.app.data.UserPreferences
import com.risediary.app.media.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.risediary.app.service.*
import java.time.Clock

data class LibraryQueueState(val assets: List<LibraryAsset> = emptyList(), val initialIndex: Int = 0, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewerViewModel @Inject constructor(factory: VideoPlayerFactory, private val repository: LibraryRepository,
    private val preferences: UserPreferences, private val usage: LibraryUsageRegistry,
    private val timer: TimerController, private val holder: TimerStateHolder, timerStore: TimerSessionStore,
    wallClock: Clock, private val elapsedClock: ElapsedRealtimeClock) : ViewModel() {
    val controller = factory.create()
    private val playback = LibraryPlaybackSession(controller)
    internal val timing = LibraryTimerSession(viewModelScope, timer, holder, timerStore::load, repository::asset, usage, wallClock, elapsedClock)
    val session = timer.state
    val persistenceError = holder.persistenceError
    val commandError = holder.commandError
    private val mutable = MutableStateFlow(LibraryQueueState())
    val state = mutable.asStateFlow()
    private val limit = MutableStateFlow(100)
    private var queue: Job? = null
    private var opened: String? = null
    private val leaseOwner = Any()
    private var selection: Job? = null
    private var timingId: String? = null
    private var lastSent: VideoPlaybackSnapshot? = null
    private var lastCheckpoint = 0L

    init {
        viewModelScope.launch { controller.interactions.collect { checkpoint(it, true) } }
        viewModelScope.launch { controller.playback.collect { snapshot ->
            if (snapshot != null && elapsedClock.millis() - lastCheckpoint >= TIMER_CHECKPOINT_MILLIS) checkpoint(snapshot, false)
        } }
        viewModelScope.launch { timer.state.collect { current ->
            if (current.sessionId == timingId && (current.finishCandidate != null || current.isTerminal)) pause()
        } }
    }

    fun open(id: String, openingId: String) {
        if (opened == id) return
        opened = id
        timingId = openingId
        timing.prepare(openingId)
        queue?.cancel()
        queue = viewModelScope.launch {
            try {
                val ready = timing.state.first { it.ready || it.error != null }
                check(ready.ready) { ready.error ?: "无法读取计时" }
                val target = ready.lockedMaterialId ?: id
                val first = repository.asset(target)?.takeIf { it.state == "ready" } ?: fallbackAsset(target)
                    ?: error("素材已不可用，请返回素材库重试")
                val initialSnapshot = repository.snapshot()
                val initialSort = preferences.librarySort.first()
                val initial = withContext(Dispatchers.Default) { LibraryTree(initialSnapshot).entries(first.folderId, "", initialSort).mapNotNull { it.asset } }
                val position = initial.indexOfFirst { it.id == target }.coerceAtLeast(0)
                limit.value = position + 100
                combine(repository.snapshots, preferences.librarySort, limit, timing.state) { snapshot, sort, size, timing ->
                    val locked = timing.lockedMaterialId
                    if (locked != null) mutable.value.assets.takeIf { list -> list.any { it.id == locked } }
                        ?: listOf(snapshot.assets.firstOrNull { it.id == locked && it.state == "ready" } ?: fallbackAsset(locked) ?: first)
                    else withContext(Dispatchers.Default) { LibraryTree(snapshot).entries(first.folderId, "", sort).mapNotNull { it.asset }.take(size) }
                }.collect { assets ->
                    mutable.value = LibraryQueueState(assets, assets.indexOfFirst { it.id == target }.coerceAtLeast(0))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val fallback = timing.state.value.lockedMaterialId?.let(::fallbackAsset)
                mutable.value = mutable.value.copy(assets = if (fallback != null) listOf(fallback) else mutable.value.assets,
                    error = e.message ?: "无法读取素材库")
            }
        }
    }

    private fun fallbackAsset(id: String): LibraryAsset? = timer.state.value.takeIf { it.sessionId == timingId }
        ?.video?.video?.let { video -> LibraryAsset(id, null, "video", video.displayName, video.displayName,
            video.mimeType ?: "video/mp4", 0L, null, "", null, 0L, "missing") }

    fun select(asset: LibraryAsset, autoplay: Boolean) {
        val locked = timing.state.value.lockedMaterialId
        if (locked != null && locked != asset.id) return
        if (controller.playback.value?.video?.libraryMaterialId() == asset.id) return
        selection?.cancel()
        val autoplayGeneration = playback.autoplayGeneration
        selection = viewModelScope.launch { try {
            check(usage.hold(leaseOwner, asset.id)) { "素材正在删除" }
            val stored = timer.state.value.takeIf { it.sessionId == timingId }?.video
            val ref = stored?.video ?: libraryVideoRef(asset.id, asset.name, asset.mimeType)
            val available = repository.asset(asset.id)?.state == "ready"
            if (!available && stored == null) error("素材已不可用")
            playback.select(ref, autoplay && !timing.state.value.started, stored, autoplayGeneration)
            mutable.value = mutable.value.copy(error = if (available) null else "素材暂时无法读取，仍可结束并保存计时")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { usage.release(leaseOwner); mutable.value = mutable.value.copy(error = "素材无法播放，请返回素材库重试") } }
    }
    fun setActive(active: Boolean) { playback.setActive(active) }
    fun onBackground() { playback.suspendAutoplay(); controller.playback.value?.let { checkpoint(it, true) } }
    fun pause() { controller.pause(); controller.playback.value?.let { checkpoint(it, true) } }
    fun start(asset: LibraryAsset) { controller.playback.value?.takeIf { it.video.libraryMaterialId() == asset.id }?.let { timing.start(asset, it) } }
    private fun checkpoint(snapshot: VideoPlaybackSnapshot, immediate: Boolean) {
        val current = timer.state.value
        if (current.sessionId != timingId || !current.isActive || current.video?.video != snapshot.video || snapshot == lastSent) return
        lastSent = snapshot; lastCheckpoint = elapsedClock.millis()
        timer.updatePlayback(requireNotNull(current.sessionId), snapshot, immediate)
    }
    fun retry() {
        if (!timing.state.value.ready) timing.retry()
        if (mutable.value.assets.isEmpty()) opened?.let { id -> opened = null; open(id, requireNotNull(timingId)) }
        else try { playback.retry(); mutable.value = mutable.value.copy(error = null) }
        catch (_: Exception) { mutable.value = mutable.value.copy(error = "素材无法播放，请重试") }
    }
    fun more() { if (mutable.value.assets.size >= limit.value) limit.value += 100 }
    override fun onCleared() { timing.close(); usage.release(leaseOwner); controller.release() }
}
