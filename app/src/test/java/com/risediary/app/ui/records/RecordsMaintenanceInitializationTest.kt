package com.risediary.app.ui.records

import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.TagRepository
import com.risediary.app.reminder.ReminderScheduler
import com.risediary.app.util.LocalCalendarContext
import java.lang.reflect.Proxy
import java.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsMaintenanceInitializationTest {
    @Test fun openingRecordsDuringMaintenanceInitializesBeforeImmediateStateCollection() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gate = DataMaintenanceGate()
        val releaseMaintenance = CompletableDeferred<Unit>()
        var owner: RecordsViewModel? = null
        val maintenance = launch { gate.maintenance { releaseMaintenance.await() } }
        try {
            runCurrent()
            assertEquals(DataMaintenanceGate.State.WORKING, gate.state.value)
            owner = records(gate)
            assertEquals(emptyList<Any>(), owner.pendingDeletions.value)
            assertEquals(emptyList<Any>(), owner.filter(emptyList()))
        } finally {
            owner?.viewModelScope?.cancel()
            releaseMaintenance.complete(Unit)
            maintenance.join()
            Dispatchers.resetMain()
        }
    }

    @Test fun openingRecordsWhenRecoveryIsRequiredInitializesBeforeImmediateStateCollection() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gate = DataMaintenanceGate().apply { requireRecovery() }
        var owner: RecordsViewModel? = null
        try {
            owner = records(gate)
            assertEquals(DataMaintenanceGate.State.RECOVERY_REQUIRED, gate.state.value)
            assertEquals(emptyList<Any>(), owner.pendingDeletions.value)
            assertEquals(emptyList<Any>(), owner.filter(emptyList()))
        } finally {
            owner?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun records(gate: DataMaintenanceGate): RecordsViewModel = RecordsViewModel(
        repository(FlightRepository::class.java, "getAllFlights"),
        repository(TagRepository::class.java, "getAllTags"),
        LocalCalendarContext(Clock.systemUTC()),
        unusedScheduler(),
        gate
    )

    private fun <T : Any> repository(type: Class<T>, flowGetter: String): T = requireNotNull(type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            if (method.name == flowGetter) flowOf(emptyList<Any>())
            else throw AssertionError("Unexpected repository call ${method.name}")
        }
    ))

    private fun unusedScheduler(): ReminderScheduler {
        // Construct the actual ViewModel, while avoiding the unused Android alarm-service
        // boundary. Neither test schedules reminders or substitutes the initialization logic.
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(ReminderScheduler::class.java) as ReminderScheduler
    }
}