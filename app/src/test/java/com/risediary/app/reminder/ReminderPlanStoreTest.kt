package com.risediary.app.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReminderPlanStoreTest {
    private val plan = ReminderPlan("stable", ReminderType.DAILY, 1_791_288_000_000L,
        "Asia/Shanghai", "daily|22:00")

    @Test fun alarmWorkerAndPersistedLedgerUseTheSameRoundTripIdentity() = runTest {
        val backing = MemoryStore()
        val original = ReminderPlanStore(backing)
        original.save(plan)
        val restored = requireNotNull(ReminderPlanStore(backing).load(ReminderType.DAILY))
        assertEquals(plan, restored)
        assertEquals(plan, ReminderPlanCodec.decode(ReminderPlanCodec.encode(restored)))
        assertEquals(reminderWorkName(ReminderType.DAILY, plan), reminderWorkName(ReminderType.DAILY, restored))
    }

    @Test fun aNewOccurrenceCannotBeSwallowedByThePreviousRunningWorkersUniqueName() {
        assertNotEquals(reminderWorkName(ReminderType.DAILY, plan),
            reminderWorkName(ReminderType.DAILY, plan.copy(id = "next")))
        assertEquals("reminder_daily", reminderWorkName(ReminderType.DAILY, null))
    }

    @Test fun consumedIsPersistedAndAnExplicitDisableClearsOnlyItsOwnType() = runTest {
        val store = ReminderPlanStore(MemoryStore())
        val monthly = plan.copy(id = "monthly", type = ReminderType.MONTHLY_LENGTH,
            configurationKey = "monthly_length|22:00|1")
        store.save(plan.copy(consumed = true))
        store.save(monthly)
        assertTrue(store.load(ReminderType.DAILY)!!.consumed)
        store.clear(ReminderType.DAILY)
        assertNull(store.load(ReminderType.DAILY))
        assertEquals(monthly, store.load(ReminderType.MONTHLY_LENGTH))
    }

    @Test fun malformedPlanDoesNotFallBackToEmptyOrOverwriteItsOriginalValue() = runTest {
        val key = stringPreferencesKey("plan_daily")
        val backing = MemoryStore(preferencesOf(key to "broken original"))
        val failure = runCatching { ReminderPlanStore(backing).load(ReminderType.DAILY) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("broken original", backing.data.value[key])
    }

    @Test fun failedSavePreservesThePreviouslyStoredIdentity() = runTest {
        val backing = MemoryStore()
        val store = ReminderPlanStore(backing)
        store.save(plan)
        backing.writeFailure = IOException("disk full")
        assertTrue(runCatching { store.save(plan.copy(id = "new")) }.exceptionOrNull() is IOException)
        assertEquals(plan, store.load(ReminderType.DAILY))
    }

    @Test fun invalidTargetOrZoneCannotBeScheduledAsAValidPayload() {
        assertTrue(runCatching { ReminderPlanCodec.encode(plan.copy(targetMillis = 0L)) }.isFailure)
        assertTrue(runCatching { ReminderPlanCodec.encode(plan.copy(zoneId = "bad/zone")) }.isFailure)
        assertTrue(runCatching { ReminderPlanCodec.decode("x".repeat(2_049)) }.isFailure)
    }

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        var writeFailure: Exception? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            return transform(data.value).also { data.value = it }
        }
    }
}
