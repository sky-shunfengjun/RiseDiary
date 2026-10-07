package com.risediary.app.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.ExistingWorkPolicy
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.repository.FlightRepository
import java.lang.reflect.Proxy
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSchedulerRecoveryTest {
    @Test fun aPlanReadFailureAtStartupRetriesWithoutClearingOrSchedulingIt() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending())
        f.plans.readFailure = IOException("disk unavailable")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.scheduler().observeConfiguration() }
        runCurrent()
        assertTrue(f.platform.schedules.isEmpty())
        assertFalse(f.platform.cancelled.contains(ReminderType.DAILY))
        f.plans.readFailure = null
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("due", f.platform.schedules.single { it.type == ReminderType.DAILY }.plan?.id)
    }

    @Test fun aPlanSaveFailureNeverSchedulesAnAnonymousTask() = runTest {
        val f = Fixture(22, 5)
        f.plans.writeFailure = IOException("disk full")
        val failure = runCatching { f.scheduler().sync(ReminderType.DAILY) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(f.platform.schedules.isEmpty())
        assertNull(f.plans.load(ReminderType.DAILY))
    }

    @Test fun anExplicitTimeChangeRecalculatesEvenWhenTheZoneAndConfigurationAreUnchanged() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending())
        f.scheduler().syncAll(recalculate = true)
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals("due", next.id)
        assertEquals(f.at(7, 22, 0), next.targetMillis)
        assertTrue(f.platform.cancelled.contains(ReminderType.DAILY))
    }

    @Test fun aWorkerThatBecomesEarlyAfterClockRollbackQueuesItsNextAttemptAfterItself() = runTest {
        val f = Fixture(21, 0)
        f.plans.save(f.pending())
        f.scheduler().rescheduleAfterFallback(ReminderType.DAILY, "due")
        assertEquals("due", f.platform.schedules.single().plan?.id)
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, f.platform.schedules.single().policy)
    }

    @Test fun anOldFallbackCannotCancelOrReplaceANewerPlan() = runTest {
        val f = Fixture(22, 5)
        val next = f.pending().copy(id = "new", targetMillis = f.at(7, 22, 0))
        f.plans.save(next)
        f.scheduler().rescheduleAfterFallback(ReminderType.DAILY, "old")
        assertEquals("new", f.platform.schedules.single().plan?.id)
        assertEquals(ExistingWorkPolicy.KEEP, f.platform.schedules.single().policy)
        assertFalse(f.platform.cancelled.contains(ReminderType.DAILY))
    }

    @Test fun coldStartupKeepsTheUndeliveredTargetInsteadOfReplacingItWithTomorrow() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.scheduler().observeConfiguration() }
        runCurrent()
        val scheduled = f.platform.schedules.single { it.type == ReminderType.DAILY }
        assertEquals(f.at(6, 22, 0), scheduled.target)
        assertEquals("due", scheduled.plan?.id)
        assertEquals(ExistingWorkPolicy.KEEP, scheduled.policy)
        assertFalse(f.platform.cancelled.contains(ReminderType.DAILY))
    }

    @Test fun aRunningFallbackKeepsItsIdentityAcrossAnotherColdScheduler() = runTest {
        val f = Fixture(22, 16)
        f.plans.save(f.pending())
        f.scheduler().sync(ReminderType.DAILY)
        f.scheduler().sync(ReminderType.DAILY)
        assertEquals(listOf("due", "due"), f.platform.schedules.map { it.plan?.id })
        assertTrue(f.platform.schedules.all { it.policy == ExistingWorkPolicy.KEEP })
        assertFalse(f.platform.cancelled.contains(ReminderType.DAILY))
    }

    @Test fun consumedOccurrenceCreatesANewPlanForTheNextDay() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending().copy(consumed = true))
        f.scheduler().sync(ReminderType.DAILY)
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals("due", next.id)
        assertFalse(next.consumed)
        assertEquals(f.at(7, 22, 0), next.targetMillis)
        assertEquals(next.id, f.platform.schedules.single().plan?.id)
    }

    @Test fun disablingAReminderRemovesItsPlanAndPendingOperations() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending())
        f.preferences.setDailyReminder(false)
        f.scheduler().sync(ReminderType.DAILY)
        assertNull(f.plans.load(ReminderType.DAILY))
        assertEquals(listOf(ReminderType.DAILY), f.platform.cancelled)
        assertTrue(f.platform.schedules.isEmpty())
    }

    @Test fun changedTimeInvalidatesTheOldPlanAndSchedulesTheNewTime() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending())
        f.preferences.setReminderTime(ReminderType.DAILY, "23:00")
        f.scheduler().sync(ReminderType.DAILY)
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals("due", next.id)
        assertEquals(f.at(6, 23, 0), next.targetMillis)
        assertEquals("daily|23:00", next.configurationKey)
        assertTrue(f.platform.cancelled.contains(ReminderType.DAILY))
    }

    @Test fun changedZoneInvalidatesTheOldPlan() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending().copy(zoneId = if (f.zone.id == "UTC") "Asia/Shanghai" else "UTC"))
        f.scheduler().sync(ReminderType.DAILY)
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals("due", next.id)
        assertEquals(f.zone.id, next.zoneId)
        assertEquals(f.at(7, 22, 0), next.targetMillis)
    }

    @Test fun midnightStartupStillKeepsThePreviousDaysPendingOccurrence() = runTest {
        val f = Fixture(0, 10, "23:55")
        f.plans.save(f.pending().copy(targetMillis = f.at(5, 23, 55), configurationKey = "daily|23:55"))
        f.scheduler().sync(ReminderType.DAILY)
        assertEquals(f.at(5, 23, 55), f.platform.schedules.single().target)
        assertEquals("due", f.platform.schedules.single().plan?.id)
    }

    @Test fun aRecordChangeDoesNotKeepAnOverduePlanFromTheOldHistory() = runTest {
        val f = Fixture(22, 5)
        f.plans.save(f.pending())
        f.scheduler().onFlightDataChanged()
        val next = requireNotNull(f.plans.load(ReminderType.DAILY))
        assertNotEquals("due", next.id)
        assertEquals(f.at(7, 22, 0), next.targetMillis)
    }

    private class Fixture(hour: Int, minute: Int, time: String = "22:00") {
        val zone = ZoneId.systemDefault()
        fun at(day: Int, hour: Int, minute: Int) = LocalDate.of(2026, 10, day)
            .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        private val clock = Clock.fixed(Instant.ofEpochMilli(at(6, hour, minute)), zone)
        val preferences = UserPreferences(MemoryPreferences(time), DataMaintenanceGate())
        val plans = MemoryPlans()
        val platform = FakePlatform()
        private val flights = Proxy.newProxyInstance(FlightRepository::class.java.classLoader,
            arrayOf(FlightRepository::class.java)) { _, method, _ ->
                check(method.name == "getRecent") { "Unexpected flight query ${method.name}" }
                emptyList<com.risediary.app.data.entity.Flight>()
            } as FlightRepository
        fun scheduler() = ReminderScheduler(preferences, flights, clock, platform, plans)
        fun pending() = ReminderPlan("due", ReminderType.DAILY, at(6, 22, 0), zone.id, "daily|22:00")
    }

    private class MemoryPlans : ReminderPlanRepository {
        private val values = mutableMapOf<ReminderType, ReminderPlan>()
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        override suspend fun load(type: ReminderType): ReminderPlan? {
            readFailure?.let { throw it }
            return values[type]
        }
        override suspend fun save(plan: ReminderPlan) {
            writeFailure?.let { throw it }
            values[plan.type] = plan
        }
        override suspend fun clear(type: ReminderType) { values.remove(type) }
    }

    private class MemoryPreferences(time: String) : DataStore<Preferences> {
        override val data = MutableStateFlow(preferencesOf(
            booleanPreferencesKey("daily_reminder_enabled") to true,
            stringPreferencesKey("daily_reminder_time") to time))
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }

    private data class Scheduled(val type: ReminderType, val target: Long,
        val policy: ExistingWorkPolicy, val plan: ReminderPlan?)
    private class FakePlatform : ReminderSchedulePlatform {
        val schedules = mutableListOf<Scheduled>()
        val cancelled = mutableListOf<ReminderType>()
        override fun schedule(type: ReminderType, targetMillis: Long, delayMillis: Long,
            policy: ExistingWorkPolicy, plan: ReminderPlan?) { schedules += Scheduled(type, targetMillis, policy, plan) }
        override fun cancel(type: ReminderType) { cancelled += type }
        override fun exactAlarmsAllowed() = true
        override fun scheduleBackgroundTest() = true
        override fun cancelBackgroundTest() = Unit
    }
}
