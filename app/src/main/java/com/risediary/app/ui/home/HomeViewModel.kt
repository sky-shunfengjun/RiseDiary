package com.risediary.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.repository.AchievementRepository
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val flightRepository: FlightRepository,
    private val lengthRepository: LengthRecordRepository,
    achievementRepository: AchievementRepository,
    preferences: UserPreferences,
    private val clock: Clock,
    private val zoneId: ZoneId
) : ViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)

    val username: StateFlow<String> =
        preferences.username.stateIn(viewModelScope, sharing, "机长")
    val homeCardOrder: StateFlow<String> =
        preferences.homeCardOrder.stateIn(viewModelScope, sharing, "[]")
    val homeCardVisibility: StateFlow<String> =
        preferences.homeCardVisibility.stateIn(viewModelScope, sharing, "{}")

    val todayCount = MutableStateFlow(0)
    val weekCount = MutableStateFlow(0)
    val monthCount = MutableStateFlow(0)
    val yearCount = MutableStateFlow(0)
    val weekSpurtSum = MutableStateFlow(0)
    val weekVolumeSum = MutableStateFlow(0f)
    val avgDuration = MutableStateFlow(0f)
    val maxDistance = MutableStateFlow(0f)
    val totalCount = MutableStateFlow(0)
    val lastWeekCount = MutableStateFlow(0)
    val averageIntervalDays = MutableStateFlow<Float?>(null)
    val isRefreshing = MutableStateFlow(false)

    val recentAchievements: StateFlow<List<Achievement>> =
        achievementRepository.allAchievements
            .map { it.take(3) }
            .stateIn(viewModelScope, sharing, emptyList())
    val lengthRecords: StateFlow<List<LengthRecord>> =
        lengthRepository.allRecords
            .stateIn(viewModelScope, sharing, emptyList())

    val currentMonthLength = MutableStateFlow<LengthRecord?>(null)
    val dailyTipResId = MutableStateFlow(getRandomTipResId())
    val lastFlightDaysAgo = MutableStateFlow<Int?>(null)
    val recentFlights = MutableStateFlow<List<Flight>>(emptyList())
    val flightCountsByDay = MutableStateFlow<Map<String, Int>>(emptyMap())
    val trendFlights = MutableStateFlow<List<Flight>>(emptyList())
    val selectedTrend = MutableStateFlow("volume")

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                coroutineScope {
                    val today = async { flightRepository.countToday() }
                    val week = async { flightRepository.countThisWeek() }
                    val month = async { flightRepository.countThisMonth() }
                    val year = async { flightRepository.countThisYear() }
                    val weekSpurts = async { flightRepository.sumSpurtThisWeek() }
                    val weekVolume = async { flightRepository.sumVolumeThisWeek() }
                    val averageDuration = async { flightRepository.avgDurationAll() }
                    val farthest = async { flightRepository.maxDistance() }
                    val total = async { flightRepository.totalCount() }
                    val previousWeek = async { flightRepository.countLastWeek() }
                    val monthLength = async { lengthRepository.getCurrentMonthRecord() }
                    val recent = async { flightRepository.getRecent(100) }
                    val allStartTimes = async { flightRepository.getAllStartTimes() }
                    val yearAgo = clock.millis() - 365L * MILLIS_PER_DAY
                    val trendStart = clock.millis() - 365L * MILLIS_PER_DAY
                    val heatmap = async { flightRepository.getDayCountsSince(yearAgo) }
                    val trends = async { flightRepository.getFlightsSince(trendStart) }

                    todayCount.value = today.await()
                    weekCount.value = week.await()
                    monthCount.value = month.await()
                    yearCount.value = year.await()
                    weekSpurtSum.value = weekSpurts.await()
                    weekVolumeSum.value = weekVolume.await()
                    avgDuration.value = averageDuration.await()
                    maxDistance.value = farthest.await()
                    totalCount.value = total.await()
                    lastWeekCount.value = previousWeek.await()
                    currentMonthLength.value = monthLength.await()

                    val recentResult = recent.await()
                    recentFlights.value = recentResult.take(30)
                    averageIntervalDays.value = calculateAverageIntervalDays(allStartTimes.await())
                    lastFlightDaysAgo.value = recentResult.firstOrNull()?.let(::daysAgo)

                    flightCountsByDay.value = heatmap.await()
                    trendFlights.value = trends.await()
                    dailyTipResId.value = getRandomTipResId()
                }
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun getGreeting(): String {
        val hour = ZonedDateTime.now(clock).hour
        return when (hour) {
            in 5..11 -> "早上好"
            in 12..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
    }

    internal fun getTodayStatus(): TodayStatus = TodayStatus(todayCount.value)

    private fun daysAgo(flight: Flight): Int {
        val flightDate = Instant.ofEpochMilli(flight.startTime).atZone(zoneId).toLocalDate()
        val today = Instant.ofEpochMilli(clock.millis()).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(flightDate, today).coerceAtLeast(0).toInt()
    }

    private fun getRandomTipResId(): Int {
        return RECORDING_TIP_RES_IDS[
            Math.floorMod(clock.millis(), RECORDING_TIP_RES_IDS.size.toLong()).toInt()
        ]
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        val RECORDING_TIP_RES_IDS = listOf(
            R.string.home_recording_tip_1,
            R.string.home_recording_tip_2,
            R.string.home_recording_tip_3,
            R.string.home_recording_tip_4,
            R.string.home_recording_tip_5,
            R.string.home_recording_tip_6,
            R.string.home_recording_tip_7,
            R.string.home_recording_tip_8,
            R.string.home_recording_tip_9,
            R.string.home_recording_tip_10
        )
    }
}

internal data class TodayStatus(val count: Int) {
    val hasRecords: Boolean
        get() = count > 0
}

internal fun calculateAverageIntervalDays(startTimes: List<Long>): Float? {
    val times = startTimes.distinct().sorted()
    if (times.size < 2) return null
    return times.zipWithNext { first, second -> second - first }
        .average()
        .div(86_400_000.0)
        .toFloat()
}
