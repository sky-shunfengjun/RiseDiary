package com.risediary.app.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.repository.AchievementRepository
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.util.LocalCalendarContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val flightRepository: FlightRepository,
    private val lengthRepository: LengthRecordRepository,
    achievementRepository: AchievementRepository,
    preferences: UserPreferences,
    private val clock: Clock,
    private val calendar: LocalCalendarContext
) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(5_000)
    val username = preferences.username.stateIn(viewModelScope, sharing, "机长")
    val homeCardOrder = preferences.homeCardOrder.stateIn(viewModelScope, sharing, "[]")
    val homeCardVisibility = preferences.homeCardVisibility.stateIn(viewModelScope, sharing, "{}")
    private val flightReads = com.risediary.app.ui.RetainedReadFlow(viewModelScope,flightRepository.allFlights,emptyList())
    private val lengthReads = com.risediary.app.ui.RetainedReadFlow(viewModelScope,lengthRepository.allRecords,emptyList())
    private val achievementReads = com.risediary.app.ui.RetainedReadFlow(viewModelScope,achievementRepository.allAchievements,emptyList())
    val recentAchievements: StateFlow<List<Achievement>> = achievementReads.data
        .map { it.take(3) }.stateIn(viewModelScope, sharing, emptyList())
    private val _statistics = MutableStateFlow(HomeStatistics(calendar.current()))
    internal val statistics = _statistics.asStateFlow()
    val calendarState = calendar.state
    val dailyTipResId = MutableStateFlow(getRandomTipResId())
    val selectedTrend = MutableStateFlow("volume")
    private var hasLoaded = false

    private val refreshCoordinator: HomeRefreshCoordinator<HomeStatistics> = HomeRefreshCoordinator(
        scope = viewModelScope,
        load = {
            val snapshot = calendar.current()
            val now = clock.millis()
            val records = coroutineScope {
                val flights = async { flightRepository.getAll() }
                val lengths = async { lengthRepository.getAll() }
                flights.await() to lengths.await()
            }
            withContext(Dispatchers.Default) {
                HomeStatistics.calculate(records.first, records.second, snapshot, now)
            }
        },
        publish = { result: HomeStatistics ->
            if (result.calendar.revision == calendar.current().revision) {
                _statistics.value = result
                dailyTipResId.value = getRandomTipResId()
                hasLoaded = true
            } else {
                refresh(silent = true)
            }
        }
    )
    val isRefreshing = refreshCoordinator.isRefreshing
    val readFailed = combine(flightReads.failed,lengthReads.failed,achievementReads.failed,refreshCoordinator.readFailed) {
        flight, length, achievement, query -> flight || length || achievement || query
    }.stateIn(viewModelScope,SharingStarted.Eagerly,false)
    fun retryRead() { flightReads.retry(); lengthReads.retry(); achievementReads.retry(); refresh() }


    init {
        // Room emits after insert/update/delete/undo/restore/clear. No main-route
        // pop or pager tab switch is needed to invalidate the home statistics.
        viewModelScope.launch {
            combine(flightReads.data, lengthReads.data, calendar.state) {
                    flights, lengths, date -> Triple(flights, lengths, date)
            }.distinctUntilChanged().collect { refresh(silent = hasLoaded) }
        }
    }

    fun refresh(silent: Boolean = false) = refreshCoordinator.request(silent)

    @StringRes
    fun getGreeting(): Int = when (clock.instant().atZone(calendar.current().zoneId).hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..13 -> R.string.home_greeting_noon
        in 14..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }

    internal fun getTodayStatus(): TodayStatus = TodayStatus(_statistics.value.todayCount)

    private fun getRandomTipResId(): Int = RECORDING_TIP_RES_IDS[
        Math.floorMod(clock.millis(), RECORDING_TIP_RES_IDS.size.toLong()).toInt()
    ]

    private companion object {
        val RECORDING_TIP_RES_IDS = listOf(
            R.string.home_recording_tip_1, R.string.home_recording_tip_2,
            R.string.home_recording_tip_3, R.string.home_recording_tip_4,
            R.string.home_recording_tip_5, R.string.home_recording_tip_6,
            R.string.home_recording_tip_7, R.string.home_recording_tip_8,
            R.string.home_recording_tip_9, R.string.home_recording_tip_10
        )
    }
}

internal data class TodayStatus(val count: Int) {
    val hasRecords: Boolean get() = count > 0
}

internal fun calculateAverageIntervalDays(startTimes: List<Long>): Float? {
    val times = startTimes.distinct().sorted()
    if (times.size < 2) return null
    return times.zipWithNext { first, second -> second - first }
        .average().div(86_400_000.0).toFloat()
}
