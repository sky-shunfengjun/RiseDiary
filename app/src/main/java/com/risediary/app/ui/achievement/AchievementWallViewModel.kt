package com.risediary.app.ui.achievement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.AchievementRepository
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.data.repository.TagJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementWallViewModel @Inject constructor(
    achievementRepo: AchievementRepository,
    private val flightRepo: FlightRepository,
    private val lengthRepo: LengthRecordRepository,
    private val detector: AchievementDetector
) : ViewModel() {

    val unlockedAchievements: StateFlow<List<Achievement>> =
        achievementRepo.allAchievements.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    /** All defined achievements (unlocked + locked with progress). */
    val allDefinitions = AchievementCatalog.definitions

    /** Progress for each achievement [0.0, 1.0]. */
    val progressMap = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun refresh() {
        viewModelScope.launch {
            val map = mutableMapOf<String, Float>()
            val total = flightRepo.totalCount()
            val streak = detector.calculateCurrentStreak()
            val totalVol = flightRepo.sumTotalVolume()
            val tags = countDistinctTags()
            val lengthCount = lengthRepo.count()
            val firstErect = lengthRepo.firstErectLength()
            val maxErect = lengthRepo.maxErectLength()

            map["milestone_1"] = if (total >= 1) 1f else 0f
            map["milestone_10"] = (total.coerceAtMost(10)).toFloat() / 10f
            map["milestone_50"] = (total.coerceAtMost(50)).toFloat() / 50f
            map["milestone_100"] = (total.coerceAtMost(100)).toFloat() / 100f
            map["milestone_500"] = (total.coerceAtMost(500)).toFloat() / 500f

            map["streak_7"] = (streak.coerceAtMost(7)).toFloat() / 7f
            map["streak_30"] = (streak.coerceAtMost(30)).toFloat() / 30f
            map["streak_90"] = (streak.coerceAtMost(90)).toFloat() / 90f

            map["volume_100ml"] = (totalVol / 100f).coerceAtMost(1f)
            map["volume_500ml"] = (totalVol / 500f).coerceAtMost(1f)

            map["tag_5_types"] = (tags.coerceAtMost(5)).toFloat() / 5f

            map["length_first"] = if (lengthCount >= 1) 1f else 0f
            map["length_growth_2cm"] = if (firstErect > 0f) {
                ((maxErect - firstErect) / 2f).coerceIn(0f, 1f)
            } else 0f

            progressMap.value = map
        }
    }

    private suspend fun countDistinctTags(): Int {
        val flights = flightRepo.getRecent(1000)
        return flights.flatMap { TagJson.decode(it.methodTags) }.distinct().size
    }
}
