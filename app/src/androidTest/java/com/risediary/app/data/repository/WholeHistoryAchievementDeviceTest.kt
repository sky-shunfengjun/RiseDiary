package com.risediary.app.data.repository

import com.risediary.app.testing.retainForTest
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.ui.achievement.AchievementWallViewModel
import com.risediary.app.util.LocalCalendarContext
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room, tag parsing, achievement detection and wall progress for a backdated fifth tag. */
@RunWith(AndroidJUnit4::class)
class WholeHistoryAchievementDeviceTest {
    @Test fun theOldestOfOneThousandAndOneRecordsUnlocksAndCompletesTagProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val owner = ViewModelStore()
        try {
            val gate = DataMaintenanceGate()
            val calendar = LocalCalendarContext(Clock.systemUTC())
            val flights = RoomFlightRepository(database.flightDao(), calendar, gate)
            val lengths = RoomLengthRecordRepository(database.lengthRecordDao(), calendar, gate)
            val achievements = RoomAchievementRepository(database.achievementDao(), gate)
            val detector = AchievementDetector(flights, lengths, achievements, calendar)
            val oldest = flight(1_000L, "fifth")
            database.withTransaction {
                repeat(1000) { index -> database.flightDao().insertNew(flight((index + 2L) * 100_000L, "tag${index % 4}")) }
                database.flightDao().insertNew(oldest)
            }
            assertTrue(detector.checkAndUnlock(oldest).any { it.key == "tag_5_types" })
            val wall = withContext(Dispatchers.Main) {
                AchievementWallViewModel(achievements, flights, lengths, detector).also {
                    owner.retainForTest("wall", it)
                    it.refresh()
                }
            }
            val progress = withTimeout(5_000L) { wall.progressMap.first { it.containsKey("tag_5_types") } }
            assertEquals(1f, progress.getValue("tag_5_types"), 0f)
        } finally {
            withContext(Dispatchers.Main) { owner.clear() }
            database.close()
        }
    }
    private fun flight(start: Long, tag: String) = Flight(
        startTime = start, endTime = start + 60_000L, durationSeconds = 60,
        spurtCount = null, semenVolumeMl = 1f, ejaculationDistanceCm = null,
        methodTags = TagJson.encode(listOf(tag)), moodNote = "", createdAt = 1_000L, updatedAt = 1_000L
    )
}