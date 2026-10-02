package com.risediary.app.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import java.time.ZoneId
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

    private class Fixture(dailyEnabled: Boolean) {
        val gate = DataMaintenanceGate()
        val events = mutableListOf<String>()
        val store = MemoryStore(dailyEnabled) { events += "marker" }
        val preferences = UserPreferences(store, gate)
        var countQuery: suspend () -> Int = { 0 }
        var notifications = 0
        private val flights = repository(FlightRepository::class.java) { method, args ->
            check(method == "countByRange") { "Unexpected flight method $method" }
            @Suppress("UNCHECKED_CAST")
            countQuery.startCoroutineUninterceptedOrReturn(args.last() as Continuation<Int>)
        }
        private val lengths = repository(LengthRecordRepository::class.java) { method, _ ->
            error("Unexpected length method $method")
        }
        val delivery = ReminderDeliveryCoordinator(
            preferences, flights, lengths,
            Clock.fixed(Instant.parse("2026-10-01T18:00:00Z"), ZoneId.of("UTC"))
        ) {
            notifications++
            events += "notification"
            true
        }
    }

    private class MemoryStore(enabled: Boolean, private val afterWrite: () -> Unit) : DataStore<Preferences> {
        var reads = 0
        private val values = MutableStateFlow(preferencesOf(booleanPreferencesKey("daily_reminder_enabled") to enabled))
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
