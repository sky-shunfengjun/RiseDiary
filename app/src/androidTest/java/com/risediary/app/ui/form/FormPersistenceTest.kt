package com.risediary.app.ui.form

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.intPreferencesKey
import com.risediary.app.data.DataMaintenanceGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
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
import org.junit.Assert.assertNull
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
    private lateinit var videoGrants: com.risediary.app.media.VideoGrantRegistry
    private lateinit var videoScope: kotlinx.coroutines.CoroutineScope
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
        videoScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        videoGrants = com.risediary.app.media.VideoGrantRegistry(
            { database.flightDao().getVideoUris().toSet() },
            com.risediary.app.media.AndroidVideoFileAccess(context),
            preferences.maintenanceGate,
            videoScope
        )
    }

    @After
    fun tearDown() {
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        videoScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
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
            initDirect(vm)
            vm.selectVolumeMode(RecordVolumeMode.MILLILITERS)
            vm.updateManualVolume("9.5")
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertFalse(vm.saved)
        assertEquals("9.5", vm.volumeMl)
        assertEquals(0, flights.getAll().size)
        assertArrayEquals(corrupt, raw.readBytes())
    }

    @Test
    fun maintenanceRejectionKeepsDraftAndEndsSavingIndicator() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            initDirect(vm)
            vm.updateEstimatedTicks(23)
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
            assertEquals(23, vm.estimatedTicks)
            assertEquals(0, flights.getAll().size)
        } finally {
            release.complete(Unit)
            maintenance.await()
        }
    }

    @Test
    fun savedManualQuantityDoesNotUseObsoleteConversion() = runBlocking {
        preferences.setMlPerSpurt(3f)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            initDirect(vm)
            vm.selectVolumeMode(RecordVolumeMode.MILLILITERS)
            vm.updateManualVolume("9.5")
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        assertEquals(9.5f, flights.getAll().single().semenVolumeMl!!, 0.001f)
        assertNull(flights.getAll().single().spurtCount)
    }

    @Test
    fun editedVisibleAmountReplacesHiddenCachedAmount() = runBlocking {
        preferences.setMlPerSpurt(2f)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            initDirect(vm)
            vm.quickVolume(6)
            vm.selectVolumeMode(RecordVolumeMode.ESTIMATED)
            vm.updateEstimatedTicks(23)
            vm.save()
        }
        awaitSaveAttempt(vm)
        val stored = flights.getAll().single()
        assertNull(stored.spurtCount)
        assertEquals(2.3f, stored.semenVolumeMl!!, 0.001f)
        assertEquals(80, stored.predictionMaxTicks)
    }

    @Test
    fun editingOnlyNotePreservesHistoricalQuantityAndSource() = runBlocking {
        preferences.setMlPerSpurt(4f)
        val previous = historicalFlight().copy(recordSource = "wearable", sourceDeviceId = "band-app-1")
        val id = flights.insert(previous)
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        withContext(Dispatchers.Main) {
            vm.setMoodNoteInput("updated")
            vm.save()
        }
        awaitSaveAttempt(vm)
        val stored = flights.getAll().single()
        assertEquals(3, stored.spurtCount)
        assertEquals(1.5f, stored.semenVolumeMl!!, 0.001f)
        assertEquals(RecordVolumeMode.MILLILITERS.storedValue, stored.volumeInputMode)
        assertEquals(previous.globalId, stored.globalId)
        assertEquals(previous.recordSource, stored.recordSource)
        assertEquals(previous.sourceDeviceId, stored.sourceDeviceId)
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
            initDirect(vm)
            vm.updateEstimatedTicks(23)
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
        withContext(Dispatchers.Main) { vm.setMoodNoteInput("retained draft") }
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
        withContext(Dispatchers.Main) { vm.setMoodNoteInput("retained draft") }
        preferences.maintenanceGate.maintenance { database.flightDao().delete(original.copy(id = id)) }

        withContext(Dispatchers.Main) { vm.save() }
        awaitSaveAttempt(vm)

        assertFalse(vm.saved)
        assertEquals("retained draft", vm.moodNote)
        assertTrue(flights.getAll().isEmpty())
        assertTrue(vm.errorMessage!!.contains("记录已变化或被移除"))
    }
    @Test
    fun zeroPredictionCannotSaveAndModeSwitchesRetainSeparateValues() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { initDirect(vm); vm.save() }
        awaitSaveAttempt(vm)
        assertFalse(vm.saved)
        assertTrue(flights.getAll().isEmpty())
        withContext(Dispatchers.Main) {
            vm.updateEstimatedTicks(23)
            vm.selectVolumeMode(RecordVolumeMode.MILLILITERS)
            vm.updateManualVolume("9.5")
            vm.selectVolumeMode(RecordVolumeMode.ESTIMATED)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        val saved = flights.getAll().single()
        assertEquals(2.3f, saved.semenVolumeMl!!, 0f)
        assertNull(saved.spurtCount)
        assertEquals("estimated", saved.volumeInputMode)
        assertEquals("9.5", vm.volumeMl)
    }

    @Test
    fun editingOldQuantityKeepsSnapshotAcrossRepeatedSaves() = runBlocking {
        val id = flights.insert(historicalFlight())
        val first = newForm()
        awaitQuantitySettings(first)
        withContext(Dispatchers.Main) { first.initForEdit(id) }
        awaitLoaded(first)
        withContext(Dispatchers.Main) { first.beginLegacyQuantityEdit(); first.selectVolumeMode(RecordVolumeMode.ESTIMATED); first.updateEstimatedTicks(23); first.save() }
        awaitSaveAttempt(first)
        val second = newForm()
        awaitQuantitySettings(second)
        withContext(Dispatchers.Main) { second.initForEdit(id) }
        awaitLoaded(second)
        withContext(Dispatchers.Main) {
            second.selectVolumeMode(RecordVolumeMode.MILLILITERS)
            second.updateManualVolume("9.5")
            second.save()
        }
        awaitSaveAttempt(second)
        val stored = flights.getAll().single()
        assertEquals(9.5f, stored.semenVolumeMl!!, 0f)
        assertNull(stored.spurtCount)
        assertEquals(3, stored.legacySpurtCount)
        assertEquals(1.5f, stored.legacyVolumeMl!!, 0f)
    }

    @Test
    fun delayedSettingsReadUsesConfiguredMaximumForLegacyQuantityEdit() = runBlocking {
        val id = flights.insert(historicalFlight())
        val release = CompletableDeferred<Unit>()
        val values = MutableStateFlow(preferencesOf(intPreferencesKey("prediction_max_ticks") to 125))
        preferences = UserPreferences(object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { release.await(); emitAll(values) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(values.value).also { values.value = it }
        }, DataMaintenanceGate())
        val vm = newForm()
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        assertFalse(vm.quantitySettingsReady)
        release.complete(Unit)
        awaitQuantitySettings(vm)
        assertEquals(125, vm.predictionMaxTicks)
        withContext(Dispatchers.Main) {
            vm.beginLegacyQuantityEdit()
            vm.selectVolumeMode(RecordVolumeMode.ESTIMATED)
            vm.updateEstimatedTicks(121)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        assertEquals(12.1f, flights.getAll().single().semenVolumeMl!!, 0f)
        assertEquals(125, flights.getAll().single().predictionMaxTicks)
    }

    @Test
    fun manualTwentyFourHourDurationPersistsWithoutTwoHourTruncation() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            initDirect(vm)
            vm.updateStartTime(clock.millis() - 86_400_000L)
            vm.updateDurationSeconds(86_400)
            vm.updateEstimatedTicks(23)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        val saved = flights.getAll().single()
        assertEquals(86_400, saved.durationSeconds)
        assertEquals(clock.millis(), saved.endTime)
    }

    @Test
    fun timerAtTwentyFourHoursFillsAndSavesTheWholeDuration() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            vm.initFromTimer(86_400_000L, clock.millis() - 86_400_000L)
            awaitLoaded(vm)
            vm.updateEstimatedTicks(23)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        val saved = flights.getAll().single()
        assertEquals(86_400, saved.durationSeconds)
        assertEquals(clock.millis() - 86_400_000L, saved.startTime)
        assertEquals(clock.millis(), saved.endTime)
    }

    @Test
    fun endTimeChangeSupportsTheFullDayAndClampsAnExtraSecond() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            initDirect(vm)
            vm.updateStartTime(clock.millis() - 86_400_000L)
            vm.updateEndTime(clock.millis() + 1_000L)
        }
        assertEquals(86_400, vm.durationSeconds)
        assertEquals(clock.millis(), vm.endTime)
    }

    @Test
    fun editingLongHistoricalDurationKeepsThenChangesItsFullValue() = runBlocking {
        val id = flights.insert(historicalFlight().copy(
            startTime = clock.millis() - 10_800_000L, durationSeconds = 10_800))
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) { vm.initForEdit(id) }
        awaitLoaded(vm)
        assertEquals(10_800, vm.durationSeconds)
        withContext(Dispatchers.Main) { vm.updateDurationSeconds(9_000); vm.save() }
        awaitSaveAttempt(vm)
        assertTrue(vm.saved)
        assertEquals(9_000, flights.getAll().single().durationSeconds)
    }

    @Test
    fun zeroDurationCannotSaveEvenWithAValidQuantity() = runBlocking {
        val vm = newForm()
        awaitQuantitySettings(vm)
        withContext(Dispatchers.Main) {
            initDirect(vm)
            vm.updateDurationSeconds(0)
            vm.updateEstimatedTicks(23)
            vm.save()
        }
        awaitSaveAttempt(vm)
        assertFalse(vm.saved)
        assertEquals(0, flights.getAll().size)
    }

    @Test
    fun videoCanBeAddedReplacedAndRemovedWithoutChangingRecordQuantityOrTimes() = runBlocking {
        val access = object : com.risediary.app.media.VideoFileAccess {
            override suspend fun acquire(uriString: String, flags: Int) = Result.success(
                com.risediary.app.media.LocalVideoRef(uriString, "selected.mp4", "video/mp4"))
            override suspend fun check(video: com.risediary.app.media.LocalVideoRef) =
                com.risediary.app.media.VideoAccessState.READABLE
            override suspend fun releaseUnused(referencedUris: Set<String>) = Unit
        }
        val registry = com.risediary.app.media.VideoGrantRegistry(
            { database.flightDao().getVideoUris().toSet() }, access, preferences.maintenanceGate, videoScope)
        val added = newForm(registry = registry)
        awaitQuantitySettings(added)
        withContext(Dispatchers.Main) {
            initDirect(added)
            added.updateEstimatedTicks(23)
            added.selectVideo(android.net.Uri.parse("content://videos/document/first"),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        awaitVideoSelection(added)
        withContext(Dispatchers.Main) { added.save() }
        awaitSaveAttempt(added)
        assertTrue(added.saved)
        val original = flights.getAll().single()
        assertEquals("content://videos/document/first", original.videoUri)

        val replaced = newForm(registry = registry)
        awaitQuantitySettings(replaced)
        withContext(Dispatchers.Main) { replaced.initForEdit(original.id) }
        awaitLoaded(replaced)
        assertEquals(original.videoUri, replaced.video?.uriString)
        withContext(Dispatchers.Main) {
            replaced.selectVideo(android.net.Uri.parse("content://videos/document/second"),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        awaitVideoSelection(replaced)
        withContext(Dispatchers.Main) { replaced.save() }
        awaitSaveAttempt(replaced)
        assertTrue(replaced.saved)
        val changed = flights.getAll().single()
        assertEquals(original.startTime, changed.startTime)
        assertEquals(original.endTime, changed.endTime)
        assertEquals(original.durationSeconds, changed.durationSeconds)
        assertEquals(original.semenVolumeMl, changed.semenVolumeMl)
        assertEquals(original.volumeInputMode, changed.volumeInputMode)
        assertEquals(original.predictionMaxTicks, changed.predictionMaxTicks)
        assertEquals("content://videos/document/second", changed.videoUri)

        val removed = newForm(registry = registry)
        awaitQuantitySettings(removed)
        withContext(Dispatchers.Main) { removed.initForEdit(original.id) }
        awaitLoaded(removed)
        withContext(Dispatchers.Main) { removed.removeVideo(); removed.save() }
        awaitSaveAttempt(removed)
        assertTrue(removed.saved)
        val result = flights.getAll().single()
        assertNull(result.videoUri)
        assertNull(result.videoDisplayName)
        assertNull(result.videoMimeType)
        assertEquals(original.semenVolumeMl, result.semenVolumeMl)
        assertEquals(original.startTime, result.startTime)
    }

    private suspend fun awaitVideoSelection(vm: FormViewModel) = withTimeout(5_000) {
        while (withContext(Dispatchers.Main) { vm.isSelectingVideo }) delay(10)
        assertNull(vm.videoError)
    }

    private suspend fun newForm(
        achievements: AchievementRepository = RoomAchievementRepository(database.achievementDao(), preferences.maintenanceGate),
        registry: com.risediary.app.media.VideoGrantRegistry = videoGrants
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
                override fun start(request: com.risediary.app.service.TimerStartRequest) = Unit
                override fun pause(sessionId: String) = Unit
                override fun resume(sessionId: String) = Unit
                override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: com.risediary.app.service.TimerFinishCandidate?) = Unit
        override fun confirmFinish(sessionId: String) = Unit
        override fun cancelFinish(sessionId: String) = Unit
        override fun updatePlayback(sessionId: String, snapshot: com.risediary.app.media.VideoPlaybackSnapshot, immediate: Boolean) = Unit
                override fun discard(sessionId: String) = Unit
                override fun reset(sessionId: String) = Unit
            },
            context,
            registry,
            RecordFormSessionStore(preferences.maintenanceGate)
        ).also { store.put(UUID.randomUUID().toString(), it) }
    }

    private suspend fun initDirect(vm: FormViewModel) {
        vm.initDirect()
        awaitLoaded(vm)
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
