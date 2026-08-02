package com.risediary.app.ui.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.entity.Tag
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.TagJson
import com.risediary.app.data.repository.TagRepository
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.service.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import com.risediary.app.util.RecordValidation
import javax.inject.Inject

@HiltViewModel
class FormViewModel @Inject constructor(
    private val flightRepository: FlightRepository,
    tagRepository: TagRepository,
    private val achievementDetector: AchievementDetector,
    private val preferences: UserPreferences,
    private val clock: Clock,
    private val reminderScheduler: ReminderScheduler,
    private val timerController: TimerController
) : ViewModel() {

    val tags: StateFlow<List<Tag>> = tagRepository.allTags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val mlPerSpurt: StateFlow<Float> = preferences.mlPerSpurt.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        2.0f
    )

    var startTime by mutableStateOf(clock.millis())
        private set
    var endTime by mutableStateOf(clock.millis())
        private set
    var durationSeconds by mutableStateOf(0)
        private set

    var useSpurtMode by mutableStateOf(false)
    var spurtCount by mutableStateOf("")
    var volumeMl by mutableStateOf("")
    var distanceCm by mutableStateOf("")
    var selectedTags by mutableStateOf<List<String>>(emptyList())
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
    var newAchievementKeys by mutableStateOf<List<String>>(emptyList())
        private set

    private var initialized = false
    private var originalFlight: Flight? = null
    private var durationWasEdited = false
    private var volumeModeTouched = false

    val hasLegacyDuration: Boolean
        get() = editingFlightId != null &&
            durationSeconds > MAX_DURATION_SECONDS &&
            !durationWasEdited

    fun initFromTimer(durationMillis: Long, timerStartTimeMillis: Long) {
        if (initialized) return
        initialized = true
        isTimerMode = true
        timerDuration = durationMillis.coerceIn(1_000L, MAX_DURATION_MILLIS)
        durationSeconds = (timerDuration / 1_000L).toInt()
        startTime = timerStartTimeMillis
            .takeIf { it > 0L }
            ?: (clock.millis() - timerDuration)
        endTime = startTime + timerDuration
        loadDefaultVolumeMode()
    }

    fun initDirect() {
        if (initialized) return
        initialized = true
        isTimerMode = false
        startTime = clock.millis()
        durationSeconds = 60
        endTime = startTime + durationSeconds * 1_000L
        loadDefaultVolumeMode()
    }

    fun initForEdit(flightId: Long) {
        if (initialized) return
        initialized = true
        isLoading = true
        viewModelScope.launch {
            val flight = flightRepository.getById(flightId)
            if (flight == null) {
                errorMessage = "记录不存在或已被删除"
            } else {
                originalFlight = flight
                editingFlightId = flight.id
                startTime = flight.startTime
                endTime = flight.endTime
                durationSeconds = flight.durationSeconds.coerceIn(
                    1,
                    RecordValidation.LEGACY_MAX_DURATION_SECONDS
                )
                spurtCount = flight.spurtCount?.toString().orEmpty()
                volumeMl = flight.semenVolumeMl?.toString().orEmpty()
                useSpurtMode = RecordVolumeMode.fromStoredValue(flight.volumeInputMode) ==
                    RecordVolumeMode.SPURTS
                distanceCm = flight.ejaculationDistanceCm?.toString().orEmpty()
                selectedTags = TagJson.decode(flight.methodTags)
                moodNote = flight.moodNote
            }
            isLoading = false
        }
    }

    fun updateStartTime(value: Long) {
        startTime = value
        recomputeEndTime()
    }

    fun updateEndTime(value: Long) {
        if (value <= startTime) {
            errorMessage = "结束时间必须晚于开始时间"
            return
        }
        val seconds = ((value - startTime) / 1_000L).toInt()
        updateDurationSeconds(seconds)
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
        volumeModeTouched = true
        if (useSpurtMode && spurtCount.isNotEmpty()) {
            spurtCount.toIntOrNull()?.let { volumeMl = formatDecimal(it * mlPerSpurt.value) }
        } else if (!useSpurtMode && volumeMl.isNotEmpty() && mlPerSpurt.value > 0f) {
            volumeMl.toFloatOrNull()?.let { spurtCount = (it / mlPerSpurt.value).toInt().toString() }
        }
        useSpurtMode = !useSpurtMode
    }

    fun setSpurtCountInput(value: String) {
        if (value.isEmpty() || value.all(Char::isDigit)) spurtCount = value.take(4)
    }

    fun setVolumeInput(value: String) {
        if (isDecimalInput(value)) volumeMl = value.take(7)
    }

    fun setDistanceInput(value: String) {
        if (isDecimalInput(value)) distanceCm = value.take(7)
    }

    fun quickSpurt(value: Int) {
        volumeModeTouched = true
        spurtCount = value.toString()
        useSpurtMode = true
    }

    fun quickVolume(value: Int) {
        volumeModeTouched = true
        volumeMl = "$value.0"
        useSpurtMode = false
    }

    fun quickDistance(value: Int) {
        distanceCm = value.toString()
    }

    fun toggleTag(tagName: String) {
        selectedTags = if (tagName in selectedTags) selectedTags - tagName
        else selectedTags + tagName
    }

    fun consumeAchievement() {
        newAchievementKeys = newAchievementKeys.drop(1)
    }

    fun save() {
        if (isSaving) return
        val validation = validate()
        if (validation != null) {
            errorMessage = validation
            return
        }

        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            try {
                val spurt = spurtCount.toIntOrNull()
                val volume = volumeMl.toFloatOrNull()
                val conversion = mlPerSpurt.value.coerceAtLeast(0.1f)
                val now = clock.millis()
                val existing = originalFlight
                val flight = Flight(
                    id = existing?.id ?: 0,
                    startTime = startTime,
                    endTime = startTime + durationSeconds * 1_000L,
                    durationSeconds = durationSeconds,
                    spurtCount = spurt ?: volume?.let { (it / conversion).toInt().coerceAtLeast(1) },
                    semenVolumeMl = volume ?: spurt?.let { it * conversion },
                    volumeInputMode = if (useSpurtMode) {
                        RecordVolumeMode.SPURTS.storedValue
                    } else {
                        RecordVolumeMode.MILLILITERS.storedValue
                    },
                    ejaculationDistanceCm = distanceCm.toFloatOrNull(),
                    methodTags = TagJson.encode(selectedTags),
                    moodNote = moodNote.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )

                if (existing == null) {
                    val id = flightRepository.insert(flight)
                    val savedFlight = flight.copy(id = id)
                    newAchievementKeys =
                        achievementDetector.checkAndUnlock(savedFlight).map { it.key }
                } else {
                    flightRepository.update(flight)
                }
                runCatching { reminderScheduler.onFlightDataChanged() }
                if (isTimerMode) {
                    runCatching { timerController.reset() }
                }
                saved = true
            } catch (error: Exception) {
                errorMessage = "保存失败：${error.message ?: "未知错误"}"
            } finally {
                isSaving = false
            }
        }
    }

    private fun validate(): String? {
        val spurt = spurtCount.toIntOrNull()
        val volume = volumeMl.toFloatOrNull()
        val distance = distanceCm.toFloatOrNull()
        return RecordValidation.validate(
            durationSeconds = durationSeconds,
            spurtCount = spurt,
            volumeMl = volume,
            distanceCm = distance,
            distanceWasEntered = distanceCm.isNotBlank(),
            allowLegacyDuration = hasLegacyDuration
        )
    }

    private fun isDecimalInput(value: String): Boolean =
        value.isEmpty() || value.matches(DECIMAL_PATTERN)

    private fun formatDecimal(value: Float): String =
        String.format(java.util.Locale.ROOT, "%.1f", value)

    private fun loadDefaultVolumeMode() {
        viewModelScope.launch {
            val mode = preferences.defaultVolumeMode.first()
            if (!volumeModeTouched && spurtCount.isEmpty() && volumeMl.isEmpty()) {
                useSpurtMode = mode == DefaultVolumeMode.SPURTS
            }
        }
    }

    private companion object {
        const val MAX_DURATION_SECONDS = RecordValidation.MAX_DURATION_SECONDS
        const val MAX_DURATION_MILLIS = MAX_DURATION_SECONDS * 1_000L
        val DECIMAL_PATTERN = Regex("""\d{0,4}(\.\d{0,2})?""")
    }
}
