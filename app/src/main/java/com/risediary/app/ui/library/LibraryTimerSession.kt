package com.risediary.app.ui.library

import com.risediary.app.data.library.LibraryAsset
import com.risediary.app.data.library.LibraryUsageRegistry
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.media.VideoSourceRef
import com.risediary.app.media.libraryMaterialId
import com.risediary.app.service.*
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class LibraryTimingState(
    val ready: Boolean = false,
    val starting: Boolean = false,
    val started: Boolean = false,
    val lockedMaterialId: String? = null,
    val error: String? = null,
    val otherTimer: TimerSession? = null
)

/** Playback never starts or resumes this timer. A request keeps its original click clocks on retry. */
internal class LibraryTimerSession(
    private val scope: CoroutineScope,
    private val controller: TimerController,
    private val holder: TimerStateHolder,
    private val readTimer: suspend () -> TimerSession,
    private val readAsset: suspend (String) -> LibraryAsset?,
    private val usage: LibraryUsageRegistry,
    private val wallClock: Clock,
    private val elapsedClock: ElapsedRealtimeClock
) {
    private val mutable = MutableStateFlow(LibraryTimingState())
    val state = mutable.asStateFlow()
    private val owner = Any()
    private var sessionId: String? = null
    private var pending: TimerStartRequest? = null
    private var preparing = false

    init {
        scope.launch {
            controller.state.collect { current ->
                if (current.sessionId == sessionId && current.status != TimerStatus.IDLE &&
                    current.videoSource is VideoSourceRef.LibraryVideo) {
                    mutable.value = state.value.copy(ready = true, started = true, starting = false,
                        lockedMaterialId = current.videoSource.materialId, error = null, otherTimer = null)
                    usage.release(owner)
                }
            }
        }
    }

    fun prepare(id: String) {
        if (preparing || (sessionId == id && state.value.ready)) return
        sessionId = id
        preparing = true
        mutable.value = state.value.copy(error = null)
        scope.launch {
            try {
                val current = holder.restoreIfIdle(readTimer)
                val material = current.videoSource as? VideoSourceRef.LibraryVideo
                if (current.sessionId == id && current.status != TimerStatus.IDLE && material != null) {
                    mutable.value = LibraryTimingState(ready = true, started = true, lockedMaterialId = material.materialId)
                    if (current.isActive) controller.restore()
                } else mutable.value = LibraryTimingState(ready = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = state.value.copy(error = "无法读取计时，请重试") }
            finally { preparing = false }
        }
    }

    fun start(asset: LibraryAsset, snapshot: VideoPlaybackSnapshot) {
        val id = sessionId ?: return
        if (!state.value.ready || state.value.started || state.value.starting || pending != null) return
        val current = controller.state.value
        if (current.status != TimerStatus.IDLE) {
            mutable.value = state.value.copy(otherTimer = current)
            return
        }
        if (snapshot.video.libraryMaterialId() != asset.id) return
        // Capture and lock synchronously, before database validation or dispatching the service.
        val request = TimerStartRequest(id, TimerKind.VIDEO, snapshot, wallClock.millis(), elapsedClock.millis(), VideoSourceRef.LibraryVideo(asset.id))
        if (!usage.hold(owner, asset.id)) {
            mutable.value = state.value.copy(error = "素材正在删除，请返回后重试")
            return
        }
        pending = request
        mutable.value = state.value.copy(starting = true, lockedMaterialId = asset.id, error = null, otherTimer = null)
        send(request, validate = true)
    }

    private fun send(request: TimerStartRequest, validate: Boolean) {
        scope.launch {
            var dispatched = false
            try {
                if (validate) check(readAsset(requireNotNull((request.videoSource as? VideoSourceRef.LibraryVideo)?.materialId))
                    ?.let { it.state == "ready" && it.mediaType == "video" } == true) { "素材已不可用" }
                if (holder.persistenceError.value) controller.retryPersistence()
                dispatched = true
                controller.start(request)
                val result = withTimeout(6_000L) {
                    combine(controller.state, holder.persistenceError, holder.commandError) { session, failed, error -> Triple(session, failed, error) }
                        .first { (session, failed, error) -> session.sessionId == request.sessionId || failed || error != null || session.status != TimerStatus.IDLE }
                }
                check(result.first.sessionId == request.sessionId && result.first.status != TimerStatus.IDLE && !result.second)
                mutable.value = state.value.copy(started = true, starting = false, error = null)
                usage.release(owner)
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                mutable.value = state.value.copy(starting = false, error = "计时尚未确认，请重试")
            } catch (_: Exception) {
                val current = controller.state.value
                val other = current.takeIf { it.status != TimerStatus.IDLE && it.sessionId != request.sessionId }
                if (!dispatched || other != null) { pending = null; usage.release(owner) }
                mutable.value = state.value.copy(starting = false,
                    lockedMaterialId = if (pending == null) null else state.value.lockedMaterialId,
                    otherTimer = other, error = if (other != null) "已有其他计时，请先返回处理" else "计时未能开始，请重试")
            }
        }
    }

    fun dismissOtherTimer() { mutable.value = state.value.copy(otherTimer = null) }
    fun retry() {
        if (state.value.starting || state.value.started) return
        val request = pending
        if (request == null) sessionId?.let(::prepare)
        else {
            mutable.value = state.value.copy(starting = true, error = null)
            send(request, validate = false)
        }
    }
    fun close() { usage.release(owner) }
}
