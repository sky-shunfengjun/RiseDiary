package com.risediary.app.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.work.ListenableWorker
import com.risediary.app.data.DataMaintenanceBusyException
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.work.ExistingWorkPolicy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderMaintenanceTest {
    @Test fun primaryThenLateFallbackStillSchedulesAndDeliversTheNextDayOnce() = runTest {
        val zone = ZoneId.systemDefault()
        val clock = MutableClock(LocalDate.of(2026, 10, 5).atTime(23, 55).atZone(zone).toInstant(), zone)
        val f = Fixture(true, "23:55", clock)
        val old = planned(LocalDate.of(2026, 10, 5))
        f.plans.save(old)
        val scheduler = ReminderScheduler(f.preferences, f.flights, clock, FakePlatform(), f.plans)
        f.delivery.deliver(ReminderType.DAILY, old)
        scheduler.sync(ReminderType.DAILY)
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals(old.id, next.id)
        assertEquals(LocalDate.of(2026, 10, 6).atTime(23, 55).atZone(zone).toInstant().toEpochMilli(), next.targetMillis)
        clock.current = LocalDate.of(2026, 10, 6).atTime(0, 10).atZone(zone).toInstant()
        f.delivery.deliver(ReminderType.DAILY, old)
        scheduler.rescheduleAfterFallback(ReminderType.DAILY, old.id)
        assertEquals(next.id, f.plans.load(ReminderType.DAILY)!!.id)
        assertEquals(1, f.notifications)
        clock.current = Instant.ofEpochMilli(next.targetMillis)
        f.delivery.deliver(ReminderType.DAILY, next)
        f.delivery.deliver(ReminderType.DAILY, next)
        assertEquals(2, f.notifications)
        assertEquals(LocalDate.of(2026, 10, 6).toEpochDay(),
            f.preferences.getReminderRuntimeState().dailyLastSentEpochDay)
    }

    @Test fun deniedAccessStillAdvancesTheNextOccurrenceOnColdStartup() = runTest {
        val zone = ZoneId.systemDefault()
        val clock = Clock.fixed(LocalDate.of(2026, 10, 6).atTime(0, 10).atZone(zone).toInstant(), zone)
        val f = Fixture(true, "23:55", clock)
        val old = planned(LocalDate.of(2026, 10, 5))
        f.plans.save(old)
        f.notificationsAllowed = false
        f.delivery.deliver(ReminderType.DAILY, old)
        ReminderScheduler(f.preferences, f.flights, clock, FakePlatform(), f.plans).sync(ReminderType.DAILY)
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals(old.id, next.id)
        assertEquals(LocalDate.of(2026, 10, 6).atTime(23, 55).atZone(zone).toInstant().toEpochMilli(), next.targetMillis)
        assertEquals(ReminderRuntimeState.UNSET_EPOCH_DAY,
            f.preferences.getReminderRuntimeState().dailyLastSentEpochDay)
    }

    @Test fun legacyPayloadCannotConsumeTheFuturePlansDate() = runTest {
        val f = lateFixture()
        val future = planned(LocalDate.of(2026, 10, 6))
        f.plans.save(future)
        f.delivery.deliver(ReminderType.DAILY)
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(),
            f.preferences.getReminderRuntimeState().dailyLastSentEpochDay)
        assertFalse(f.plans.load(ReminderType.DAILY)!!.consumed)
    }

    @Test fun anExplicitLateTargetChecksAndMarksItsOwnDayInsteadOfTheExecutionDay() = runTest {
        val fixture = lateFixture()
        val plan = planned(LocalDate.of(2026, 10, 4))
        fixture.plans.save(plan)
        fixture.delivery.deliver(ReminderType.DAILY, plan)
        assertEquals(LocalDate.of(2026, 10, 4).toEpochDay(),
            fixture.preferences.getReminderRuntimeState().dailyLastSentEpochDay)
        val zone = ZoneId.systemDefault()
        assertEquals(LocalDate.of(2026, 10, 4).atStartOfDay(zone).toInstant().toEpochMilli(),
            fixture.lastQuery!!.first)
        assertEquals(LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli(),
            fixture.lastQuery!!.second)
    }

    @Test fun alarmAndFallbackConsumeTheSamePlanOnlyOnce() = runTest {
        val fixture = lateFixture()
        val plan = planned(LocalDate.of(2026, 10, 5))
        fixture.plans.save(plan)
        fixture.delivery.deliver(ReminderType.DAILY, plan)
        fixture.delivery.deliver(ReminderType.DAILY, plan)
        assertEquals(1, fixture.notifications)
        assertTrue(fixture.plans.load(ReminderType.DAILY)!!.consumed)
    }

    @Test fun anOldPayloadCannotDeliverTheNewPlansNotification() = runTest {
        val fixture = lateFixture()
        val old = planned(LocalDate.of(2026, 10, 5))
        fixture.plans.save(old.copy(id = "new"))
        fixture.delivery.deliver(ReminderType.DAILY, old)
        assertEquals(0, fixture.notifications)
        assertFalse(fixture.plans.load(ReminderType.DAILY)!!.consumed)
    }

    @Test fun deniedNotificationsConsumeTheOccurrenceWithoutMarkingItAsSent() = runTest {
        val fixture = lateFixture()
        val plan = planned(LocalDate.of(2026, 10, 5))
        fixture.plans.save(plan)
        fixture.notificationsAllowed = false
        fixture.delivery.deliver(ReminderType.DAILY, plan)
        assertEquals(0, fixture.notifications)
        assertTrue(fixture.plans.load(ReminderType.DAILY)!!.consumed)
        assertEquals(ReminderRuntimeState.UNSET_EPOCH_DAY,
            fixture.preferences.getReminderRuntimeState().dailyLastSentEpochDay)
    }

    @Test fun futureTargetCannotBeDeliveredAfterTheWallClockMovesBack() = runTest {
        val fixture = lateFixture()
        val plan = planned(LocalDate.of(2026, 10, 6))
        fixture.plans.save(plan)
        fixture.delivery.deliver(ReminderType.DAILY, plan)
        assertEquals(0, fixture.notifications)
        assertFalse(fixture.plans.load(ReminderType.DAILY)!!.consumed)
    }

    @Test fun changingTheConfiguredTimeRejectsTheOldPayload() = runTest {
        val fixture = lateFixture()
        val plan = planned(LocalDate.of(2026, 10, 5))
        fixture.plans.save(plan)
        fixture.preferences.setReminderTime(ReminderType.DAILY, "21:00")
        fixture.delivery.deliver(ReminderType.DAILY, plan)
        assertEquals(0, fixture.notifications)
    }

    private fun lateFixture(): Fixture {
        val zone = ZoneId.systemDefault()
        return Fixture(true, "23:55", Clock.fixed(
            LocalDate.of(2026, 10, 6).atTime(0, 10).atZone(zone).toInstant(), zone))
    }

    private fun planned(day: LocalDate) = ReminderPlan("pending", ReminderType.DAILY,
        day.atTime(23, 55).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        ZoneId.systemDefault().id, "daily|23:55")

    @Test fun aDailyFallbackAfterMidnightUsesThePreviousScheduledDay() = runTest {
        val zone = ZoneId.systemDefault()
        val yesterday = LocalDate.of(2026, 10, 5)
        val fixture = Fixture(dailyEnabled = true, dailyTime = "23:55", clock = Clock.fixed(
            LocalDate.of(2026, 10, 6).atTime(0, 10).atZone(zone).toInstant(), zone))
        fixture.delivery.deliver(ReminderType.DAILY)
        assertEquals(1, fixture.notifications)
        assertEquals("Fallback must not consume the next day's notification",
            yesterday.toEpochDay(), fixture.preferences.getReminderRuntimeState().dailyLastSentEpochDay)
    }

    @Test fun maintenanceRefusesDeliveryBeforeReadingOrPosting() = runTest {
        val fixture = Fixture(dailyEnabled = false)
        fixture.gate.requireRecovery()
        val failure = runCatching { fixture.delivery.deliver(ReminderType.DAILY) }.exceptionOrNull()
        assertTrue(failure is DataMaintenanceBusyException)
        assertEquals(0, fixture.store.reads)
        assertEquals(0, fixture.notifications)
    }

    @Test fun inFlightDeliveryCommitsItsMarkerBeforeMaintenanceCanReplaceData() = runTest {
        val fixture = Fixture(dailyEnabled = true)
        val query = CompletableDeferred<Int>()
        fixture.countQuery = { query.await() }
        val delivery = async { fixture.delivery.deliver(ReminderType.DAILY) }
        runCurrent()
        val maintenance = launch {
            fixture.gate.maintenance { fixture.events += "maintenance" }
        }
        runCurrent()
        assertEquals(emptyList<String>(), fixture.events)
        query.complete(0)
        delivery.await()
        maintenance.join()
        assertEquals(listOf("notification", "marker", "maintenance"), fixture.events)
        assertEquals(1, fixture.notifications)
        assertTrue(fixture.preferences.getReminderRuntimeState().dailyLastSentEpochDay >= 0)
    }

    @Test fun workManagerRetriesMaintenanceBusyWithoutRescheduling() = runTest {
        var rescheduled = false
        val result = runReminderWork(
            deliver = { throw DataMaintenanceBusyException() },
            reschedule = { rescheduled = true }
        )
        assertEquals(ListenableWorker.Result.retry(), result)
        assertFalse(rescheduled)
    }

    @Test fun actualCoroutineCancellationRemainsCancellation() = runTest {
        val cancelled = CancellationException("worker stopped")
        val failure = runCatching {
            runReminderWork(deliver = { throw cancelled }, reschedule = { fail("must not run") })
        }.exceptionOrNull()
        assertSame(cancelled, failure)
    }

    private class Fixture(dailyEnabled: Boolean, dailyTime: String = "22:00",
        clock: Clock = Clock.fixed(Instant.parse("2026-10-01T18:00:00Z"), ZoneId.of("UTC"))) {
        val gate = DataMaintenanceGate()
        val events = mutableListOf<String>()
        val store = MemoryStore(dailyEnabled, dailyTime) { events += "marker" }
        val preferences = UserPreferences(store, gate)
        val plans = MemoryPlans()
        var countQuery: suspend () -> Int = { 0 }
        var lastQuery: Pair<Long, Long>? = null
        var notifications = 0
        var notificationsAllowed = true
        val flights = repository(FlightRepository::class.java) { method, args ->
            if (method == "getRecent") emptyList<com.risediary.app.data.entity.Flight>() else {
                check(method == "countByRange") { "Unexpected flight method $method" }
                lastQuery = (args[0] as Long) to (args[1] as Long)
                @Suppress("UNCHECKED_CAST")
                countQuery.startCoroutineUninterceptedOrReturn(args.last() as Continuation<Int>)
            }
        }
        private val lengths = repository(LengthRecordRepository::class.java) { method, _ ->
            error("Unexpected length method $method")
        }
        val delivery = ReminderDeliveryCoordinator(
            preferences, flights, lengths,
            clock, plans
        ) {
            if (!notificationsAllowed) false else {
                notifications++
                events += "notification"
                true
            }
        }
    }

    private class MemoryPlans : ReminderPlanRepository {
        private val values = mutableMapOf<ReminderType, ReminderPlan>()
        override suspend fun load(type: ReminderType) = values[type]
        override suspend fun save(plan: ReminderPlan) { values[plan.type] = plan }
        override suspend fun clear(type: ReminderType) { values.remove(type) }
    }

    private class MutableClock(var current: Instant, private val currentZone: ZoneId) : Clock() {
        override fun getZone() = currentZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant() = current
    }

    private class FakePlatform : ReminderSchedulePlatform {
        override fun schedule(type: ReminderType, targetMillis: Long, delayMillis: Long,
            policy: ExistingWorkPolicy, plan: ReminderPlan?) = Unit
        override fun cancel(type: ReminderType) = Unit
        override fun exactAlarmsAllowed() = true
        override fun scheduleBackgroundTest() = true
        override fun cancelBackgroundTest() = Unit
    }

    private class MemoryStore(enabled: Boolean, time: String, private val afterWrite: () -> Unit) : DataStore<Preferences> {
        var reads = 0
        private val values = MutableStateFlow(preferencesOf(booleanPreferencesKey("daily_reminder_enabled") to enabled,
            stringPreferencesKey("daily_reminder_time") to time))
        override val data: Flow<Preferences> = flow { reads++; emit(values.value) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            values.value = transform(values.value)
            afterWrite()
            return values.value
        }
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> repository(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                invoke(method.name, args ?: emptyArray())
            } as T
    }
}
