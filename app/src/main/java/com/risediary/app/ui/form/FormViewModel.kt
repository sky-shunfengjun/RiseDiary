package com.risediary.app.ui.form

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.DefaultVolumeMode
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
    @ApplicationContext private val context: Context
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
    var useSpurtMode by mutableStateOf(false)
        private set
    var spurtCount by mutableStateOf("")
        private set
    var volumeMl by mutableStateOf("")
        private set
    var distanceCm by mutableStateOf("")
        private set
    var quickSpurtSelection by mutableStateOf<Int?>(null)
        private set
    var quickVolumeSelection by mutableStateOf<Int?>(null)
        private set
    var quickDistanceSelection by mutableStateOf<Int?>(null)
        private set
    var selectedTags by mutableStateOf<List<String>>(emptyList())
        private set
    var moodNote by mutableStateOf("")

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

    private var initialized = false
    private var durationWasEdited = false
    private var volumeModeTouched = false
    private var quantitySettings: QuantitySettingsSnapshot? = null
    private var quantitySettingsJob: Job? = null
    private var quantityDraft = RecordFormQuantityDraft()
    private val recordSaver = FormRecordSaveWorkflow(
        flightRepository::insert, flightRepository::update, flightRepository::getById
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
                    if (!volumeModeTouched && originalFlight == null && spurtCount.isEmpty() && volumeMl.isEmpty()) {
                        useSpurtMode = snapshot.defaultVolumeMode == DefaultVolumeMode.SPURTS
                    }
                    syncQuantityDisplay()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                quantitySettings = null
                quantitySettingsReady = false
                isQuantitySettingsLoading = false
                quantitySettingsError = "无法读取射精量设置，请重试；当前草稿已保留"
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
    }

    fun initDirect() {
        if (initialized) return
        initialized = true
        isTimerMode = false
        startTime = clock.millis()
        durationSeconds = 60
        endTime = startTime + durationSeconds * 1_000L
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
                    quantityDraft = RecordFormQuantityDraft(flight)
                    editingFlightId = flight.id
                    startTime = flight.startTime
                    endTime = flight.endTime
                    durationSeconds = flight.durationSeconds.coerceIn(1, RecordValidation.LEGACY_MAX_DURATION_SECONDS)
                    spurtCount = flight.spurtCount?.toString().orEmpty()
                    volumeMl = flight.semenVolumeMl?.toString().orEmpty()
                    useSpurtMode = RecordVolumeMode.fromStoredValue(flight.volumeInputMode) == RecordVolumeMode.SPURTS
                    distanceCm = flight.ejaculationDistanceCm?.toString().orEmpty()
                    selectedTags = TagJson.decode(flight.methodTags)
                    moodNote = flight.moodNote
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
        startTime = value
        recomputeEndTime()
    }

    fun updateEndTime(value: Long) {
        if (value <= startTime) {
            errorMessage = context.getString(R.string.form_error_end_before_start)
            return
        }
        val seconds = ((value - startTime) / 1_000L).coerceIn(0L, MAX_DURATION_SECONDS.toLong())
        updateDurationSeconds(seconds.toInt())
    }

    fun updateDurationSeconds(value: Int) {
        durationWasEdited = true
        durationSeconds = value.coerceIn(0, MAX_DURATION_SECONDS)
        recomputeEndTime()
    }

    private fun recomputeEndTime() {
        endTime = startTime + durationSeconds * 1_000L
    }

    fun toggleSpurtMode() {
        if (!quantitySettingsReady) return
        volumeModeTouched = true
        quickSpurtSelection = null
        quickVolumeSelection = null
        useSpurtMode = !useSpurtMode
        syncQuantityDisplay()
    }

    fun setSpurtCountInput(value: String) {
        if (value.isEmpty() || value.all(Char::isDigit)) {
            spurtCount = value.take(4)
            quantityDraft.enter(RecordVolumeMode.SPURTS, spurtCount)
            volumeModeTouched = true
            syncQuantityDisplay()
        }
        quickSpurtSelection = null
    }

    fun setVolumeInput(value: String) {
        if (isDecimalInput(value)) {
            volumeMl = value.take(7)
            quantityDraft.enter(RecordVolumeMode.MILLILITERS, volumeMl)
            volumeModeTouched = true
            syncQuantityDisplay()
        }
        quickVolumeSelection = null
    }

    fun setDistanceInput(value: String) {
        if (isDecimalInput(value)) distanceCm = value.take(7)
        quickDistanceSelection = null
    }

    fun setMoodNoteInput(value: String) {
        val noteError = RecordValidation.validateNote(value, originalFlight?.moodNote)
        if (noteError == null) moodNote = value else errorMessage = noteError
    }

    fun quickSpurt(value: Int) {
        useSpurtMode = true
        setSpurtCountInput(value.toString())
        quickSpurtSelection = value
        quickVolumeSelection = null
    }

    fun quickVolume(value: Int) {
        useSpurtMode = false
        setVolumeInput("$value.0")
        quickVolumeSelection = value
        quickSpurtSelection = null
    }

    fun quickDistance(value: Int) {
        distanceCm = value.toString()
        quickDistanceSelection = value
    }

    fun toggleTag(tagName: String) {
        selectedTags = if (tagName in selectedTags) selectedTags - tagName else selectedTags + tagName
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
        if (isSaving || isLoading || saved) return
        isSaving = true
        errorMessage = null
        val saveJob = preferences.maintenanceGate.launchWrite(viewModelScope) {
            try {
                // A fresh strict read fixes both initial-load and stale-setting races.
                val snapshot = preferences.quantitySettings.first()
                requireValidSettings(snapshot)
                quantitySettings = snapshot
                quantitySettingsReady = true
                quantitySettingsError = null
                val values = quantityDraft.resolve(snapshot.mlPerSpurt).getOrThrow()
                val validation = RecordValidation.validate(
                    durationSeconds, values.spurtCount, values.semenVolumeMl,
                    distanceCm.toFloatOrNull(), distanceCm.isNotBlank(), hasLegacyDuration
                ) ?: RecordValidation.validateNote(moodNote, originalFlight?.moodNote)
                if (validation != null) {
                    errorMessage = validation
                    return@launchWrite
                }
                val now = clock.millis()
                val existing = originalFlight
                val flight = Flight(
                    id = existing?.id ?: 0,
                    startTime = startTime,
                    endTime = startTime + durationSeconds * 1_000L,
                    durationSeconds = durationSeconds,
                    spurtCount = values.spurtCount,
                    semenVolumeMl = values.semenVolumeMl,
                    volumeInputMode = quantityDraft.inputModeForSave.storedValue,
                    ejaculationDistanceCm = distanceCm.toFloatOrNull(),
                    methodTags = TagJson.encode(selectedTags),
                    moodNote = if (existing != null && moodNote == existing.moodNote) moodNote else moodNote.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                val followUps = mutableListOf<suspend () -> Unit>({ reminderScheduler.onFlightDataChanged() })
                if (isTimerMode) followUps += { timerController.reset() }
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
            } catch (_: StaleRecordDraftException) {
                errorMessage = "这条记录已变化或被移除；当前草稿已保留，请返回后重新打开"
            } catch (_: DataMaintenanceBusyException) {
                errorMessage = "数据恢复或清除中，请稍后再保存；当前草稿已保留"
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
                    errorMessage = "数据恢复或清除中，请稍后再保存；当前草稿已保留"
                    isSaving = false
                }
            }
        }
    }

    private fun syncQuantityDisplay() {
        val conversion = quantitySettings?.mlPerSpurt ?: return
        spurtCount = quantityDraft.display(RecordVolumeMode.SPURTS, conversion)
        volumeMl = quantityDraft.display(RecordVolumeMode.MILLILITERS, conversion)
    }

    private fun requireValidSettings(snapshot: QuantitySettingsSnapshot) {
        require(snapshot.mlPerSpurt.isFinite() && snapshot.mlPerSpurt in 0.1f..100f) {
            "每股毫升设置无效，请重试读取设置"
        }
    }

    private fun isDecimalInput(value: String): Boolean = value.isEmpty() || value.matches(DECIMAL_PATTERN)

    private companion object {
        const val MAX_DURATION_SECONDS = RecordValidation.MAX_DURATION_SECONDS
        const val MAX_DURATION_MILLIS = MAX_DURATION_SECONDS * 1_000L
        val DECIMAL_PATTERN = Regex("""\d{0,4}(\.\d{0,2})?""")
    }
}