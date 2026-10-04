package com.risediary.app.ui.form

import android.content.Context
import android.net.Uri
import com.risediary.app.media.LocalVideoRef
import com.risediary.app.media.VideoGrantRegistry
import com.risediary.app.media.localVideoRef
import com.risediary.app.media.validateLocalVideoFields
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.QuantitySettingsSnapshot
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.entity.Tag
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.TagJson
import com.risediary.app.data.repository.TagRepository
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.service.TimerController
import com.risediary.app.util.RecordValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class FormViewModel @Inject constructor(
    private val flightRepository: FlightRepository,
    tagRepository: TagRepository,
    private val achievementDetector: AchievementDetector,
    private val preferences: UserPreferences,
    private val clock: Clock,
    private val reminderScheduler: ReminderScheduler,
    private val timerController: TimerController,
    @ApplicationContext private val context: Context,
    private val videoGrants: VideoGrantRegistry,
    private val forms: RecordFormSessionStore
) : ViewModel() {
    val tags: StateFlow<List<Tag>> = tagRepository.allTags.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    var startTime by mutableStateOf(clock.millis())
        private set
    var endTime by mutableStateOf(clock.millis())
        private set
    var durationSeconds by mutableStateOf(0)
        private set
    var useEstimatedMode by mutableStateOf(true)
        private set
    var estimatedTicks by mutableStateOf(0)
        private set
    var predictionMaxTicks by mutableStateOf(80)
        private set
    var isLegacyQuantityReadOnly by mutableStateOf(false)
        private set
    var legacyQuantityText by mutableStateOf("")
        private set
    var volumeMl by mutableStateOf("")
        private set
    var distanceCm by mutableStateOf("")
        private set
    var quickVolumeSelection by mutableStateOf<Int?>(null)
        private set
    var quickDistanceSelection by mutableStateOf<Int?>(null)
        private set
    var selectedTags by mutableStateOf<List<String>>(emptyList())
        private set
    var moodNote by mutableStateOf("")
        private set

    var isTimerMode by mutableStateOf(false)
        private set
    var timerDuration by mutableStateOf(0L)
        private set
    var editingFlightId by mutableStateOf<Long?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var saveWarning by mutableStateOf<String?>(null)
        private set
    var newAchievementKeys by mutableStateOf<List<String>>(emptyList())
        private set
    var isQuantitySettingsLoading by mutableStateOf(true)
        private set
    var quantitySettingsError by mutableStateOf<String?>(null)
        private set
    var quantitySettingsReady by mutableStateOf(false)
        private set

    var video by mutableStateOf<LocalVideoRef?>(null)
        private set
    var isSelectingVideo by mutableStateOf(false)
        private set
    var videoError by mutableStateOf<String?>(null)
        private set
    private val videoOwner = "form:" + UUID.randomUUID()

    fun selectVideo(uri: Uri, flags: Int) {
        if (isSelectingVideo || isSaving || isLoading || saved) return
        isSelectingVideo = true
        videoError = null
        viewModelScope.launch {
            try {
                val current = listOfNotNull(originalFlight?.videoUri, video?.uriString).toSet()
                video = videoGrants.acquire(videoOwner, uri.toString(), flags, current).getOrThrow()
                updateVideoPin()
                updateSession()
                videoGrants.requestCleanup()
            } catch (_: DataMaintenanceBusyException) {
                videoError = "数据处理中，请稍后再选择视频"
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                videoError = "无法读取这个视频，请重新选择"
            } finally { isSelectingVideo = false }
        }
    }

    fun removeVideo() {
        if (isSelectingVideo || isSaving || isLoading || saved) return
        video = null
        videoError = null
        // Update memory before grant-mutex suspension, including an immediate Back or Save.
        updateSession()
        viewModelScope.launch {
            updateVideoPin()
            videoGrants.requestCleanup()
        }
    }

    private suspend fun updateVideoPin() {
        videoGrants.retain(videoOwner, listOfNotNull(originalFlight?.videoUri, video?.uriString).toSet())
    }

    override fun onCleared() {
        formSnapshot?.let { forms.discard(it.formId) }
        videoGrants.forget(videoOwner)
    }

    var formReady by mutableStateOf(false)
        private set
    var sessionExpired by mutableStateOf(false)
        private set
    var showDiscard by mutableStateOf(false)
        private set
    private var formSnapshot: RecordFormSessionSnapshot? = null
    private var editBaseline: RecordFormSessionSnapshot? = null
    private var timingSource = "manual"

    private fun captureForm(seed: RecordFormSessionSnapshot? = formSnapshot) =
        seed?.copy(startTime = startTime, endTime = endTime,
            durationSeconds = durationSeconds, timingSource = timingSource, timeWasEdited = durationWasEdited,
            quantity = quantityDraft.snapshot(), video = video, distanceText = distanceCm,
            methodTags = selectedTags, moodNote = moodNote)

    private fun updateSession() {
        if (!formReady || saved || isLoading || isSaving || editingFlightId != null) return
        captureForm()?.let { forms.update(it); formSnapshot = it }
    }

    private fun attachSession(snapshot: RecordFormSessionSnapshot) {
        formSnapshot = snapshot
        startTime = snapshot.startTime
        endTime = snapshot.endTime
        durationSeconds = snapshot.durationSeconds
        durationWasEdited = snapshot.timeWasEdited
        timingSource = snapshot.timingSource
        isTimerMode = snapshot.sessionId != null
        timerDuration = snapshot.durationSeconds * 1_000L
        quantityDraft = RecordFormQuantityDraft.restore(snapshot.quantity)
        quantityRangeInitialized = true
        video = snapshot.video
        distanceCm = snapshot.distanceText
        selectedTags = snapshot.methodTags
        moodNote = snapshot.moodNote
        syncQuantityDisplay()
        formReady = true
        viewModelScope.launch { updateVideoPin() }
    }

    fun initSession(formId: String?) {
        if (initialized) return
        initialized = true
        val snapshot = formId?.let(forms::get)
        if (snapshot == null) sessionExpired = true else attachSession(snapshot)
    }

    // Direct callers can seed a form; navigation passes an already-created memory session.
    private fun createInitialSession() {
        isLoading = true
        viewModelScope.launch {
            try {
                val settings = preferences.quantitySettings.first()
                requireValidSettings(settings)
                val seed = if (isTimerMode) forms.createFromTimer(com.risediary.app.service.TimerSession(
                    status = com.risediary.app.service.TimerStatus.FINISHED,
                    sessionId = UUID.randomUUID().toString(), startedAtEpochMillis = startTime,
                    endedAtEpochMillis = endTime, elapsedMillis = timerDuration
                ), settings.predictionMaxTicks) else forms.createManual(startTime, settings.predictionMaxTicks)
                attachSession(seed)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { errorMessage = "无法打开填写页面，请返回后重试" }
            finally { isLoading = false }
        }
    }

    val hasUnsavedContent: Boolean
        get() = if (saved) false else if (editingFlightId != null) {
            editBaseline?.let { baseline ->
                val current = captureForm(baseline)
                current?.copy(quantity = current.quantity.copy(predictionMaxTicks = baseline.quantity.predictionMaxTicks)) != baseline
            } ?: false
        } else formSnapshot?.let { forms.hasUnsavedContent(it.formId) } ?: false

    private var leaving = false
    fun leave(onReady: () -> Unit) {
        if (isSaving || isLoading || isSelectingVideo || leaving) return
        if (hasUnsavedContent) showDiscard = true else discardAndLeave(onReady)
    }

    fun cancelDiscard() { showDiscard = false }

    fun discardAndLeave(onReady: () -> Unit) {
        if (isSaving || isLoading || isSelectingVideo || leaving) return
        leaving = true
        showDiscard = false
        formSnapshot?.let { forms.discard(it.formId) }
        videoGrants.forget(videoOwner)
        onReady()
    }
    private var initialized = false
    private var durationWasEdited = false
    private var quantityRangeInitialized = false
    private var quantitySettings: QuantitySettingsSnapshot? = null
    private var quantitySettingsJob: Job? = null
    private var quantityDraft = RecordFormQuantityDraft()
    private val recordSaver = FormRecordSaveWorkflow(
        flightRepository::insertOnce, flightRepository::update, flightRepository::getById
    )
    private val originalFlight: Flight?
        get() = recordSaver.persistedFlight

    val hasLegacyDuration: Boolean
        get() = editingFlightId != null &&
            durationSeconds > MAX_DURATION_SECONDS && !durationWasEdited

    init {
        retryQuantitySettings()
    }

    fun retryQuantitySettings() {
        quantitySettingsJob?.cancel()
        quantitySettingsReady = false
        isQuantitySettingsLoading = true
        quantitySettingsError = null
        quantitySettingsJob = viewModelScope.launch {
            try {
                preferences.quantitySettings.collect { snapshot ->
                    requireValidSettings(snapshot)
                    quantitySettings = snapshot
                    quantitySettingsReady = true
                    isQuantitySettingsLoading = false
                    quantitySettingsError = null
                    if (!quantityRangeInitialized) {
                        quantityDraft = if (originalFlight == null) RecordFormQuantityDraft.restore(
                            quantityDraft.snapshot().copy(predictionMaxTicks = snapshot.predictionMaxTicks)
                        ) else RecordFormQuantityDraft(originalFlight, snapshot.predictionMaxTicks)
                        quantityRangeInitialized = true
                    }
                    syncQuantityDisplay()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                quantitySettings = null
                quantitySettingsReady = false
                isQuantitySettingsLoading = false
                quantitySettingsError = "无法读取射精量设置，请重试；当前输入已保留"
            }
        }
    }

    fun initFromTimer(durationMillis: Long, timerStartTimeMillis: Long) {
        if (initialized) return
        initialized = true
        isTimerMode = true
        timerDuration = durationMillis.coerceIn(1_000L, MAX_DURATION_MILLIS)
        durationSeconds = (timerDuration / 1_000L).toInt()
        startTime = timerStartTimeMillis.takeIf { it > 0L } ?: (clock.millis() - timerDuration)
        endTime = startTime + timerDuration
        createInitialSession()
    }

    fun initDirect() {
        if (initialized) return
        initialized = true
        isTimerMode = false
        startTime = clock.millis()
        durationSeconds = 60
        endTime = startTime + durationSeconds * 1_000L
        createInitialSession()
    }

    fun initForEdit(flightId: Long) {
        if (initialized) return
        initialized = true
        isLoading = true
        viewModelScope.launch {
            try {
                val flight = flightRepository.getById(flightId)
                if (flight == null) {
                    errorMessage = context.getString(R.string.form_error_record_missing)
                } else {
                    recordSaver.loadOriginal(flight)
                    video = flight.localVideoRef()
                    updateVideoPin()
                    quantityDraft = RecordFormQuantityDraft(flight, quantitySettings?.predictionMaxTicks ?: 80)
                    editingFlightId = flight.id
                    timingSource = flight.timingSource
                    formReady = true
                    startTime = flight.startTime
                    endTime = flight.endTime
                    durationSeconds = flight.durationSeconds.coerceIn(1, RecordValidation.LEGACY_MAX_DURATION_SECONDS)
                    quantityRangeInitialized = flight.predictionMaxTicks != null || quantitySettings != null
                    syncQuantityDisplay()
                    distanceCm = flight.ejaculationDistanceCm?.toString().orEmpty()
                    selectedTags = TagJson.decode(flight.methodTags)
                    moodNote = flight.moodNote
                    editBaseline = RecordFormSessionSnapshot(
                        formId = videoOwner, submissionId = "edit:" + flight.id,
                        startTime = startTime, endTime = endTime, durationSeconds = durationSeconds,
                        timingSource = timingSource, quantity = quantityDraft.snapshot(),
                        video = video, distanceText = distanceCm, methodTags = selectedTags, moodNote = moodNote
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorMessage = "无法读取这条记录，请返回后重试"
            } finally {
                isLoading = false
            }
        }
    }

    fun updateStartTime(value: Long) {
        if (isSaving || isLoading || saved) return
        startTime = value
        durationWasEdited = true
        timingSource = "manual"
        recomputeEndTime()
        updateSession()
    }

    fun updateEndTime(value: Long) {
        if (isSaving || isLoading || saved) return
        if (value <= startTime) {
            errorMessage = context.getString(R.string.form_error_end_before_start)
            return
        }
        val seconds = ((value - startTime) / 1_000L).coerceIn(0L, MAX_DURATION_SECONDS.toLong())
        updateDurationSeconds(seconds.toInt())
    }

    fun updateDurationSeconds(value: Int) {
        if (isSaving || isLoading || saved) return
        durationWasEdited = true
        timingSource = "manual"
        durationSeconds = value.coerceIn(0, MAX_DURATION_SECONDS)
        recomputeEndTime()
        updateSession()
    }

    private fun recomputeEndTime() {
        endTime = startTime + durationSeconds * 1_000L
    }

    fun selectVolumeMode(mode: RecordVolumeMode) {
        if (isSaving || isLoading || saved) return
        if (!quantitySettingsReady || isLegacyQuantityReadOnly) return
        quantityDraft.selectMode(mode)
        quickVolumeSelection = null
        syncQuantityDisplay()
        updateSession()
    }

    fun updateEstimatedTicks(value: Int) {
        if (!quantitySettingsReady || isLoading || isSaving || saved || isLegacyQuantityReadOnly) return
        quantityDraft.setEstimatedTicks(value)
        syncQuantityDisplay()
        updateSession()
    }

    fun beginLegacyQuantityEdit() {
        if (!quantitySettingsReady || isLoading || isSaving || saved) return
        quantityDraft.beginLegacyQuantityEdit()
        syncQuantityDisplay()
        updateSession()
    }

    fun updateManualVolume(value: String) {
        if (isSaving || isLoading || saved) return
        if (isDecimalInput(value)) {
            quantityDraft.setManualText(value.take(7))
            syncQuantityDisplay()
        }
        quickVolumeSelection = null
        updateSession()
    }

    fun setDistanceInput(value: String) {
        if (isSaving || isLoading || saved) return
        if (isDecimalInput(value)) distanceCm = value.take(7)
        quickDistanceSelection = null
        updateSession()
    }

    fun setMoodNoteInput(value: String) {
        if (isSaving || isLoading || saved) return
        val noteError = RecordValidation.validateNote(value, originalFlight?.moodNote)
        if (noteError == null) { moodNote = value; updateSession() } else errorMessage = noteError
    }

    fun quickVolume(value: Int) {
        if (isSaving || isLoading || saved) return
        selectVolumeMode(RecordVolumeMode.MILLILITERS)
        updateManualVolume("$value.0")
        quickVolumeSelection = value
    }

    fun quickDistance(value: Int) {
        if (isSaving || isLoading || saved) return
        distanceCm = value.toString()
        quickDistanceSelection = value
        updateSession()
    }

    fun toggleTag(tagName: String) {
        if (isSaving || isLoading || saved) return
        selectedTags = if (tagName in selectedTags) selectedTags - tagName else selectedTags + tagName
        updateSession()
    }

    fun consumeAchievement(key: String? = null) {
        if (key == null || newAchievementKeys.firstOrNull() == key) {
            newAchievementKeys = newAchievementKeys.drop(1)
        }
    }

    fun dismissSaveWarning() {
        saveWarning = null
    }

    fun save() {
        if (isSaving || isLoading || isSelectingVideo || saved || !formReady) return
        updateSession()
        isSaving = true
        errorMessage = null
        val saveJob = viewModelScope.launch {
            try {
                preferences.maintenanceGate.write {
                formSnapshot?.let { preferences.maintenanceGate.requireGeneration(it.dataGeneration) }
                // A fresh strict read fixes both initial-load and stale-setting races.
                val snapshot = preferences.quantitySettings.first()
                requireValidSettings(snapshot)
                quantitySettings = snapshot
                quantitySettingsReady = true
                quantitySettingsError = null
                val values = quantityDraft.resolve().getOrThrow()
                val validation = RecordValidation.validate(
                    durationSeconds, values.spurtCount, values.semenVolumeMl,
                    distanceCm.toFloatOrNull(), distanceCm.isNotBlank(), hasLegacyDuration
                ) ?: RecordValidation.validateNote(moodNote, originalFlight?.moodNote)
                    ?: validateLocalVideoFields(video?.uriString, video?.displayName, video?.mimeType)
                    ?: com.risediary.app.util.RecordTimingPolicy.validate(startTime, endTime, durationSeconds, timingSource, hasLegacyDuration)
                if (validation != null) {
                    errorMessage = validation
                    return@write
                }
                val now = clock.millis()
                val existing = originalFlight
                val flight = Flight(
                    id = existing?.id ?: 0,
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = durationSeconds,
                    spurtCount = values.spurtCount,
                    semenVolumeMl = values.semenVolumeMl,
                    volumeInputMode = quantityDraft.inputModeForSave.storedValue,
                    ejaculationDistanceCm = distanceCm.toFloatOrNull(),
                    methodTags = TagJson.encode(selectedTags),
                    moodNote = if (existing != null && moodNote == existing.moodNote) moodNote else moodNote.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    videoUri = video?.uriString,
                    videoDisplayName = video?.displayName,
                    videoMimeType = video?.mimeType,
                    recordDraftId = existing?.recordDraftId ?: formSnapshot?.submissionId,
                    timingSource = timingSource
                ).let(quantityDraft::applyTo)
                val followUps = mutableListOf<suspend () -> Unit>({ reminderScheduler.onFlightDataChanged() })
                val result = recordSaver.save(
                    flight,
                    afterInsert = { achievementDetector.checkAndUnlock(it).map { achievement -> achievement.key } },
                    followUps = followUps
                )
                newAchievementKeys = result.achievementKeys
                if (result.followUpFailures.isNotEmpty()) {
                    saveWarning = "记录已保存，但成就、提醒或计时收尾未能全部完成。已保存的记录不会重复新增。"
                }
                saved = true
                formSnapshot?.let { forms.discard(it.formId) }
                }
            } catch (_: StaleRecordDraftException) {
                errorMessage = "这条记录已变化或被移除；当前输入已保留，请返回后重新打开"
            } catch (_: DataMaintenanceBusyException) {
                errorMessage = "数据恢复或清除中，请稍后再保存；当前输入已保留"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = context.getString(
                    R.string.form_error_save_failed,
                    error.message ?: context.getString(R.string.form_error_unknown)
                )
            } finally {
                isSaving = false
            }
        }
        saveJob.invokeOnCompletion { cause ->
            if (cause is DataMaintenanceBusyException) {
                viewModelScope.launch {
                    errorMessage = "数据恢复或清除中，请稍后再保存；当前输入已保留"
                    isSaving = false
                }
            }
        }
    }

    private fun syncQuantityDisplay() {
        val snapshot = quantityDraft.snapshot()
        useEstimatedMode = snapshot.selectedMode == RecordVolumeMode.ESTIMATED.storedValue
        estimatedTicks = snapshot.estimatedTicks
        predictionMaxTicks = snapshot.predictionMaxTicks
        isLegacyQuantityReadOnly = quantityDraft.isLegacyQuantityReadOnly
        legacyQuantityText = originalFlight?.let(com.risediary.app.util.RecordQuantityDisplay::current).orEmpty()
        volumeMl = snapshot.manualText
    }

    private fun requireValidSettings(snapshot: QuantitySettingsSnapshot) {
        com.risediary.app.util.PredictionQuantitySettings.requireMaximum(snapshot.predictionMaxTicks)
    }

    private fun isDecimalInput(value: String): Boolean = value.isEmpty() || value.matches(DECIMAL_PATTERN)

    private companion object {
        const val MAX_DURATION_SECONDS = RecordValidation.MAX_DURATION_SECONDS
        const val MAX_DURATION_MILLIS = com.risediary.app.util.DurationPolicy.MAX_MILLIS
        val DECIMAL_PATTERN = Regex("""\d{0,4}(\.\d{0,2})?""")
    }
}
