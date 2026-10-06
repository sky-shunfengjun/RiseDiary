package com.risediary.app.ui.form

import com.risediary.app.testing.retainForTest
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.risediary.app.data.*
import com.risediary.app.data.draft.*
import com.risediary.app.data.entity.*
import com.risediary.app.data.repository.*
import com.risediary.app.media.*
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.service.*
import com.risediary.app.util.LocalCalendarContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordFormSessionLifecycleTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: UserPreferences
    private lateinit var forms: RecordFormSessionStore
    private lateinit var initialForm: RecordFormSessionSnapshot
    private lateinit var flights: RoomFlightRepository
    private lateinit var grants: VideoGrantRegistry
    private lateinit var scope: CoroutineScope
    private var models = ViewModelStore()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_100_000L), ZoneOffset.UTC)
    private val video = LocalVideoRef("content://videos/document/1", "视频", "video/mp4")
    private fun initial() = TimerSession(status = TimerStatus.FINISHED, sessionId = "session",
        startedAtEpochMillis = 1_700_000_000_000L, endedAtEpochMillis = 1_700_000_012_000L,
        elapsedMillis = 8_000L, kind = TimerKind.VIDEO, video = VideoPlaybackSnapshot(video))

    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = File(base.cacheDir, "draft-recovery-" + UUID.randomUUID())
        context = object : ContextWrapper(base) { override fun getFilesDir() = files }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        preferences = UserPreferences(context)
        forms = RecordFormSessionStore(preferences.maintenanceGate)
        initialForm = forms.createFromTimer(initial(), 80)
        flights = RoomFlightRepository(database.flightDao(), LocalCalendarContext(clock), preferences.maintenanceGate)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        grants = VideoGrantRegistry({
            database.flightDao().getVideoUris().toSet() + forms.videoUris()
        }, object : VideoFileAccess {
            override suspend fun acquire(uriString: String, flags: Int) = Result.success(video)
            override suspend fun check(video: LocalVideoRef) = VideoAccessState.READABLE
            override suspend fun releaseUnused(referencedUris: Set<String>) = Unit
        }, preferences.maintenanceGate, scope)
    }
    @After fun cleanup() = runBlocking {
        withContext(Dispatchers.Main) { models.clear() }
        scope.cancel()
        database.close()
    }
    private suspend fun model(videoRegistry: VideoGrantRegistry = grants, formId: String = initialForm.formId) = withContext(Dispatchers.Main) {
        val calendar = LocalCalendarContext(clock)
        FormViewModel(flights, RoomTagRepository(database.tagDao(), database, preferences.maintenanceGate),
            AchievementDetector(flights, RoomLengthRecordRepository(database.lengthRecordDao(), calendar, preferences.maintenanceGate),
                RoomAchievementRepository(database.achievementDao(), preferences.maintenanceGate), calendar),
            preferences, clock, ReminderScheduler(context, preferences, flights, clock),
            object : TimerController {
                override val state = MutableStateFlow(TimerSession())
                override fun restore() = Unit
                override fun start(request: TimerStartRequest) = Unit
                override fun pause(sessionId: String) = Unit
                override fun resume(sessionId: String) = Unit
                override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long, candidate: TimerFinishCandidate?) = Unit
                override fun confirmFinish(sessionId: String) = Unit
                override fun cancelFinish(sessionId: String) = Unit
                override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) = Unit
                override fun discard(sessionId: String) = Unit
                override fun reset(sessionId: String) = Unit
            }, context, videoRegistry, forms).also {
            models.retainForTest(UUID.randomUUID().toString(), it)
            it.initSession(formId)
        }
    }
    private suspend fun ready(vm: FormViewModel) = withTimeout(8_000L) {
        while (withContext(Dispatchers.Main) { vm.isLoading || !vm.quantitySettingsReady || !vm.formReady }) delay(10)
    }
    private suspend fun saved(vm: FormViewModel) = withTimeout(8_000L) {
        while (withContext(Dispatchers.Main) { !vm.saved && vm.errorMessage == null }) delay(10)
    }

    @Test fun liveFormKeepsBothInputsAndActualEndWithoutWritingDrafts() = runBlocking {
        val vm = model()
        ready(vm)
        withContext(Dispatchers.Main) {
            vm.updateEstimatedTicks(23)
            vm.selectVolumeMode(RecordVolumeMode.MILLILITERS)
            vm.updateManualVolume("9.5")
            vm.selectVolumeMode(RecordVolumeMode.ESTIMATED)
            vm.setMoodNoteInput("当前填写")
            vm.toggleTag("手动")
            vm.removeVideo()
        }
        val current = forms.get(initialForm.formId)!!
        assertEquals(23, current.quantity.estimatedTicks)
        assertEquals("9.5", current.quantity.manualText)
        assertEquals("当前填写", current.moodNote)
        assertNull(current.video)
        assertNull(database.recordDraftDao().getPending())
        withContext(Dispatchers.Main) { vm.save(); vm.save() }
        saved(vm)
        assertTrue(vm.saved)
        val record = flights.getAll().single()
        assertEquals(1_700_000_012_000L, record.endTime)
        assertEquals(8, record.durationSeconds)
        assertEquals("timer", record.timingSource)
        assertEquals("session", record.recordDraftId)
        assertNull(record.videoUri)
        assertNull(forms.get(initialForm.formId))
        assertEquals(2.3f, record.semenVolumeMl!!, 0.001f)
    }

    @Test fun abandoningTimerFormNeedsConfirmationAndCancelKeepsTheInputs() = runBlocking {
        val vm = model()
        ready(vm)
        var left = false
        withContext(Dispatchers.Main) {
            vm.setMoodNoteInput("尚未保存")
            vm.leave { left = true }
            assertTrue(vm.showDiscard)
            assertFalse(left)
            vm.cancelDiscard()
            assertFalse(vm.showDiscard)
            assertEquals("尚未保存", vm.moodNote)
            vm.leave { left = true }
            vm.discardAndLeave { left = true }
        }
        assertTrue(left)
        assertNull(forms.get(initialForm.formId))
        assertNull(database.recordDraftDao().getPending())
        assertTrue(flights.getAll().isEmpty())
    }

    @Test fun clearingThePageCannotRecoverItsPreviousForm() = runBlocking {
        val vm = model()
        ready(vm)
        withContext(Dispatchers.Main) {
            vm.updateEstimatedTicks(23)
            models.clear()
            models = ViewModelStore()
        }
        assertNull(forms.get(initialForm.formId))
        val reopened = model()
        assertTrue(withContext(Dispatchers.Main) { reopened.sessionExpired })
        assertFalse(withContext(Dispatchers.Main) { reopened.formReady })
        assertNull(database.recordDraftDao().getPending())
    }

    @Test fun unchangedManualFormCanLeaveWithoutConfirmation() = runBlocking {
        val seed = forms.createManual(clock.millis(), 80)
        val vm = model(formId = seed.formId)
        ready(vm)
        var left = false
        withContext(Dispatchers.Main) {
            vm.leave { left = true }
            assertFalse(vm.showDiscard)
        }
        assertTrue(left)
        assertNull(forms.get(seed.formId))
    }

    @Test fun attachmentRemovalUpdatesMemoryEvenWhileItsGrantPinWaits() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delayedRegistry = VideoGrantRegistry({ emptySet() }, object : VideoFileAccess {
            override suspend fun acquire(uriString: String, flags: Int): Result<LocalVideoRef> {
                entered.complete(Unit)
                release.await()
                return Result.success(video)
            }
            override suspend fun check(video: LocalVideoRef) = VideoAccessState.READABLE
            override suspend fun releaseUnused(referencedUris: Set<String>) = Unit
        }, DataMaintenanceGate(), scope)
        val vm = model(delayedRegistry)
        ready(vm)
        val holdingPin = scope.launch { delayedRegistry.acquire("holding", video.uriString, 0, emptySet()) }
        try {
            withTimeout(5_000L) { entered.await() }
            withContext(Dispatchers.Main) { vm.removeVideo() }
            assertNull(forms.get(initialForm.formId)!!.video)
            assertNull(database.recordDraftDao().getPending())
        } finally { release.complete(Unit); holdingPin.join() }
    }

    @Test fun changingDurationSwitchesToManualEndTimeAndKeepsVideo() = runBlocking {
        val vm = model()
        ready(vm)
        withContext(Dispatchers.Main) {
            vm.updateEstimatedTicks(23)
            vm.updateDurationSeconds(10)
            vm.save()
        }
        saved(vm)
        assertTrue(vm.saved)
        val record = flights.getAll().single()
        assertEquals("manual", record.timingSource)
        assertEquals(1_700_000_010_000L, record.endTime)
        assertEquals(10, record.durationSeconds)
        assertEquals(video.uriString, record.videoUri)
    }

    @Test fun oldFormCannotRepopulateDataAfterDatabaseReplacement() = runBlocking {
        val vm = model()
        ready(vm)
        val replacement = Flight(id = 7L, startTime = 1_700_000_000_000L, endTime = 1_700_000_060_000L,
            durationSeconds = 60, spurtCount = null, semenVolumeMl = 2f,
            ejaculationDistanceCm = null, methodTags = "[]", moodNote = "恢复的数据")
        preferences.maintenanceGate.maintenance {
            database.withTransaction { database.flightDao().insert(replacement) }
        }
        withContext(Dispatchers.Main) {
            vm.setMoodNoteInput("旧页面的输入")
            vm.updateEstimatedTicks(23)
            vm.save()
        }
        saved(vm)
        assertFalse(vm.saved)
        assertEquals(listOf(replacement), flights.getAll())
        assertEquals("旧页面的输入", vm.moodNote)
        assertNull(database.recordDraftDao().getPending())
    }
}
