package com.risediary.app.ui.achievement

import androidx.lifecycle.viewModelScope
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.repository.*
import com.risediary.app.util.LocalCalendarContext
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementReadFailureTest {
    @Test fun progressAndUnlockReadFailuresRetainDataAndRetryBoth() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var failUnlockRead = true; var failProgress = false; var count = 5
        val unlocked = listOf(Achievement(id = 1, achievementKey = "milestone_1", unlockedAt = 1000))
        val achievements = proxy(AchievementRepository::class.java) { name ->
            when (name) {
                "getAllAchievements" -> flow<List<Achievement>> { emit(unlocked); if (failUnlockRead) throw IOException("unlocks failed") }
                else -> throw AssertionError(name)
            }
        }
        val flights = proxy(FlightRepository::class.java) { name ->
            when (name) {
                "totalCount" -> { if (failProgress) throw IOException("progress failed"); count }
                "getDistinctFlightDates", "getAllMethodTags" -> emptyList<String>()
                "sumTotalVolume" -> 0f
                else -> throw AssertionError(name)
            }
        }
        val lengths = proxy(LengthRecordRepository::class.java) { name ->
            when (name) { "count" -> 0; "firstErectLength" -> null; "maxErectLength" -> 0f; else -> throw AssertionError(name) }
        }
        val detector = AchievementDetector(flights, lengths, achievements, LocalCalendarContext(Clock.systemUTC()))
        val vm = AchievementWallViewModel(achievements, flights, lengths, detector)
        try {
            vm.refresh(); runCurrent()
            assertEquals(unlocked, vm.unlockedAchievements.value)
            assertTrue(vm.readFailed.value)
            val previous = vm.progressMap.value
            failProgress = true; vm.refresh(); runCurrent()
            assertEquals(previous, vm.progressMap.value)
            failUnlockRead = false; failProgress = false; count = 7
            vm.retryRead(); runCurrent()
            assertFalse(vm.readFailed.value)
            assertEquals(unlocked, vm.unlockedAchievements.value)
            assertEquals(0.7f, vm.progressMap.value["milestone_10"])
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private fun <T : Any> proxy(type: Class<T>, call: (String) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> call(method.name) })!!
}
