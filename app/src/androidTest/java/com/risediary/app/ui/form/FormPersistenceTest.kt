package com.risediary.app.ui.form

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.repository.AchievementDetector
import com.risediary.app.data.repository.AchievementRepository
import com.risediary.app.data.repository.RoomAchievementRepository
import com.risediary.app.data.repository.RoomFlightRepository
import com.risediary.app.data.repository.RoomLengthRecordRepository
import com.risediary.app.data.repository.RoomTagRepository
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerSession
import com.risediary.app.util.LocalCalendarContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Real FormViewModel + Room writes; only the failing achievement boundary is substituted. */
@RunWith(AndroidJUnit4::class)
class FormPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var flights: RoomFlightRepository
    private lateinit var store: ViewModelStore
    private val clock = Clock.fixed(Instant.parse("2026-10-02T04:00:00Z"), ZoneId.of("UTC"))

    @Before
    fun setUp() {
        store = ViewModelStore()
        val base = ApplicationProvider.getApplicationContext<Context>()
        val isolatedFiles = File(base.cacheDir, "form-review-${UUID.randomUUID()}")
        context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = isolatedFiles
        }
        preferences = UserPreferences(context)
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        flights = RoomFlightRepository(database.flightDao(), LocalCalendarContext(clock), preferences.maintenanceGate)
    }

    @After
    fun tearDown() {
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        database.close()
    }

    @Test
    fun corruptQuantitySettingsCannotSaveUsingDisplayDefaultsAndFileIsPreserved() = runBlocking {
        val corrupt = byteArrayOf(0x0a, 0x7f, 0x01)
        val raw = File(context.filesDir, "datastore/settings.preferences_pb")
        raw.parentFile!!.mkdirs()
        raw.writeBytes(corrupt)
        val vm = newForm()
        withTimeout(5_000) {
            while (withContext(Dispatchers.Main) { vm.quantitySettingsError == null }) delay(10)
        }
        withContext(Dispatchers.Main) {
            vm.initDirect()
            vm.quickSpurt(10)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertFalse(vm.saved)
        assertEquals("10", vm.spurtCount)
        assertEquals(0, flights.getAll().size)
        assertArrayEquals(corrupt, raw.readBytes())
    }

    @Test
    fun maintenanceRejectionKeepsDraftAndEndsSavingIndicator() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            vm.initDirect()
            vm.quickSpurt(3)
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val maintenance = async {
            preferences.maintenanceGate.maintenance {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        try {
            withContext(Dispatchers.Main) { vm.save() }
            awaitSaveAttempt(vm)
            assertFalse(vm.saved)
            assertFalse(vm.isSaving)
            assertEquals("3", vm.spurtCount)
            assertEquals(0, flights.getAll().size)
        } finally {
            release.complete(Unit)
            maintenance.await()
        }
    }

    @Test
    fun savedSpurtsUseCurrentPreferenceInsteadOfUnsubscribedDefault() = runBlocking {
        preferences.setMlPerSpurt(3f)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            vm.initDirect()
            vm.quickSpurt(10)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        assertEquals(30f, flights.getAll().single().semenVolumeMl!!, 0.001f)
    }

    @Test
    fun editedVisibleAmountReplacesHiddenCachedAmount() = runBlocking {
        preferences.setMlPerSpurt(2f)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            vm.initDirect()
            vm.quickVolume(6)
            vm.toggleSpurtMode()
            vm.setSpurtCountInput("10")
            vm.save()
        }
        awaitSaveAttempt(vm)
        val stored = flights.getAll().single()
        assertEquals(10, stored.spurtCount)
        assertEquals(20f, stored.semenVolumeMl!!, 0.001f)
    }

    @Test
    fun editingOnlyNoteAndDisplayedUnitPreservesHistoricalQuantityAndSource() = runBlocking {
        preferences.setMlPerSpurt(4f)
        val previous = historicalFlight()
        val id = flights.insert(previous)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        withContext(Dispatchers.Main) {
            vm.toggleSpurtMode()
            vm.moodNote = "updated"
            vm.save()
        }
        awaitSaveAttempt(vm)
        val stored = flights.getAll().single()
        assertEquals(3, stored.spurtCount)
        assertEquals(1.5f, stored.semenVolumeMl!!, 0.001f)
        assertEquals(RecordVolumeMode.MILLILITERS.storedValue, stored.volumeInputMode)
    }

    @Test
    fun achievementFailureAfterCommitDoesNotReportLostRecordOrInsertAgain() = runBlocking {
        val actual = RoomAchievementRepository(database.achievementDao(), preferences.maintenanceGate)
        val failing = object : AchievementRepository by actual {
            override suspend fun unlock(key: String): Achievement? = throw IOException("unavailable")
        }
        val vm = newForm(failing)
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            vm.initDirect()
            vm.quickSpurt(3)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue("Primary record was committed even though achievement failed", vm.saved)
        withContext(Dispatchers.Main) { vm.save() }
        delay(100)
        assertEquals(1, flights.getAll().size)
    }

    @Test
    fun savingOtherFieldsDoesNotTrimOrTruncateHistoricalLongNote() = runBlocking {
        val note = "  " + "x".repeat(10_001) + "  "
        val id = flights.insert(historicalFlight().copy(moodNote = note))
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        withContext(Dispatchers.Main) {
            vm.updateStartTime(clock.millis() - 120_000)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        assertEquals(note, flights.getAll().single().moodNote)
    }

    @Test
    fun staleDraftCannotOverwriteARecordRestoredWithTheSameId() = runBlocking {
        val original = historicalFlight()
        val id = flights.insert(original)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        withContext(Dispatchers.Main) { vm.moodNote = "retained draft" }
        val restored = original.copy(id = id, moodNote = "restored record", updatedAt = clock.millis() + 1_000)
        preferences.maintenanceGate.maintenance { database.flightDao().update(restored) }

        withContext(Dispatchers.Main) { vm.save() }
        awaitSaveAttempt(vm)

        assertFalse(vm.saved)
        assertFalse(vm.isSaving)
        assertEquals("retained draft", vm.moodNote)
        assertEquals(restored, flights.getById(id))
        assertTrue(vm.errorMessage!!.contains("记录已变化或被移除"))
    }

    @Test
    fun staleDraftCannotSaveAfterItsRecordWasRemovedByMaintenance() = runBlocking {
        val original = historicalFlight()
        val id = flights.insert(original)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        withContext(Dispatchers.Main) { vm.moodNote = "retained draft" }
        preferences.maintenanceGate.maintenance { database.flightDao().delete(original.copy(id = id)) }

        withContext(Dispatchers.Main) { vm.save() }
        awaitSaveAttempt(vm)

        assertFalse(vm.saved)
        assertEquals("retained draft", vm.moodNote)
        assertTrue(flights.getAll().isEmpty())
        assertTrue(vm.errorMessage!!.contains("记录已变化或被移除"))
    }
    private suspend fun newForm(
        achievements: AchievementRepository = RoomAchievementRepository(database.achievementDao(), preferences.maintenanceGate)
    ): FormViewModel = withContext(Dispatchers.Main) {
        FormViewModel(
            flights,
            RoomTagRepository(database.tagDao(), database, preferences.maintenanceGate),
            AchievementDetector(
                flights,
                RoomLengthRecordRepository(database.lengthRecordDao(), LocalCalendarContext(clock), preferences.maintenanceGate),
                achievements,
                LocalCalendarContext(clock)
            ),
            preferences,
            clock,
            ReminderScheduler(context, preferences, flights, clock),
            object : TimerController {
                override val state = MutableStateFlow(TimerSession())
                override fun restore() = Unit
                override fun start() = Unit
                override fun pause() = Unit
                override fun resume() = Unit
                override fun finish() = Unit
                override fun reset() = Unit
            },
            context
        ).also { store.put(UUID.randomUUID().toString(), it) }
    }

    private suspend fun awaitQuantitySettings(vm: FormViewModel) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { vm.quantitySettingsReady }) delay(10)
    }

    private suspend fun awaitLoaded(vm: FormViewModel) = withTimeout(5_000) {
        while (withContext(Dispatchers.Main) { vm.isLoading }) delay(10)
    }

    private suspend fun awaitSaveAttempt(vm: FormViewModel) = withTimeout(5_000) {
        while (!withContext(Dispatchers.Main) { vm.saved || vm.errorMessage != null }) delay(10)
    }

    private fun historicalFlight() = Flight(
        startTime = clock.millis() - 60_000,
        endTime = clock.millis(),
        durationSeconds = 60,
        spurtCount = 3,
        semenVolumeMl = 1.5f,
        volumeInputMode = RecordVolumeMode.MILLILITERS.storedValue,
        ejaculationDistanceCm = null,
        methodTags = "[]",
        moodNote = "old",
        createdAt = clock.millis() - 60_000,
        updatedAt = clock.millis()
    )
}