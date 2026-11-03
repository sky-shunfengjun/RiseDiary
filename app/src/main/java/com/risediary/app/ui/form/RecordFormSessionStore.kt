package com.risediary.app.ui.form

import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoSourceRef
import com.risediary.app.media.requireVideoSource
import com.risediary.app.data.library.LibraryUsageRegistry
import com.risediary.app.service.TimerSession
import com.risediary.app.util.PredictionQuantitySettings
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Lives in this process only. Neither form inputs nor navigation IDs are written to disk. */
data class RecordFormSessionSnapshot(
    val formId: String,
    val submissionId: String,
    val sessionId: String? = null,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val timingSource: String = "manual",
    val timeWasEdited: Boolean = false,
    val quantity: QuantityDraftSnapshot,
    val video: LocalVideoRef? = null,
    val distanceText: String = "",
    val methodTags: List<String> = emptyList(),
    val moodNote: String = "",
    val dataGeneration: Long = 0L,
    val videoSource: VideoSourceRef? = video?.let(VideoSourceRef::DeviceVideo),
    val removedMaterialName: String? = null
)

@Singleton
class RecordFormSessionStore @Inject constructor(
    private val gate: com.risediary.app.data.DataMaintenanceGate,
    private val usage: LibraryUsageRegistry = LibraryUsageRegistry()
) {
    constructor() : this(com.risediary.app.data.DataMaintenanceGate())

    private data class Entry(val initial: RecordFormSessionSnapshot, var current: RecordFormSessionSnapshot)
    private val forms = mutableMapOf<String, Entry>()

    @Synchronized
    fun createManual(now: Long, predictionMaxTicks: Int): RecordFormSessionSnapshot =
        add(RecordFormSessionSnapshot(
            formId = UUID.randomUUID().toString(), submissionId = UUID.randomUUID().toString(),
            startTime = now, endTime = now + 60_000L, durationSeconds = 60,
            quantity = emptyQuantity(predictionMaxTicks), dataGeneration = gate.snapshotGeneration()
        ))

    @Synchronized
    fun createFromTimer(finished: TimerSession, predictionMaxTicks: Int): RecordFormSessionSnapshot {
        require(finished.isTerminal && finished.finishCandidate == null)
        val submissionId = requireNotNull(finished.sessionId)
        require(submissionId.isNotBlank())
        forms.values.firstOrNull { it.current.submissionId == submissionId }?.let { return it.current }
        val duration = (finished.elapsedMillis / 1_000L).coerceIn(1L, 86_400L).toInt()
        return add(RecordFormSessionSnapshot(
            formId = UUID.randomUUID().toString(), submissionId = submissionId, sessionId = submissionId,
            startTime = finished.startedAtEpochMillis,
            endTime = finished.endedAtEpochMillis ?: (finished.startedAtEpochMillis + duration * 1_000L),
            durationSeconds = duration,
            timingSource = if (finished.endedAtEpochMillis == null) "manual" else "timer",
            quantity = emptyQuantity(predictionMaxTicks), dataGeneration = gate.snapshotGeneration(), video = finished.video?.video,
            videoSource = finished.videoSource
        ))
    }

    @Synchronized
    fun get(formId: String): RecordFormSessionSnapshot? = forms[formId]?.current

    @Synchronized
    fun update(snapshot: RecordFormSessionSnapshot) {
        val entry = checkNotNull(forms[snapshot.formId]) { "填写页面已关闭" }
        require(snapshot.submissionId == entry.initial.submissionId && snapshot.sessionId == entry.initial.sessionId && snapshot.dataGeneration == entry.initial.dataGeneration)
        val effective = if (snapshot.video == null && snapshot.videoSource is VideoSourceRef.DeviceVideo)
            snapshot.copy(videoSource = null) else snapshot
        protect(effective)
        entry.current = effective
    }

    @Synchronized
    fun discard(formId: String) { forms.remove(formId); usage.release("form-session:$formId") }

    @Synchronized
    fun hasUnsavedContent(formId: String): Boolean = forms[formId]?.let {
        it.current.sessionId != null || it.current != it.initial
    } ?: false

    @Synchronized
    fun videoUris(): Set<String> = forms.values.mapNotNull { (it.current.videoSource as? VideoSourceRef.DeviceVideo)?.video?.uriString }.toSet()

    private fun add(snapshot: RecordFormSessionSnapshot): RecordFormSessionSnapshot {
        protect(snapshot)
        forms[snapshot.formId] = Entry(snapshot, snapshot)
        return snapshot
    }

    private fun protect(snapshot: RecordFormSessionSnapshot) {
        requireVideoSource(snapshot.video, snapshot.videoSource)
        val material = snapshot.videoSource as? VideoSourceRef.LibraryVideo
        if (material != null) check(usage.hold("form-session:${snapshot.formId}", material.materialId)) { "素材正在删除" }
        else usage.release("form-session:${snapshot.formId}")
    }

    private fun emptyQuantity(maximum: Int) = QuantityDraftSnapshot(
        "estimated", 0, "", PredictionQuantitySettings.requireMaximum(maximum), false
    )
}
