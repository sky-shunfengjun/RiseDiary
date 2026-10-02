package com.risediary.app.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Real ViewModel and preference API, substituting only the external disk boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
class CardOrderPersistenceTest {
    @Test
    fun importedLayoutCannotBeOverwrittenByThePageLoadedBeforeImport() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakePreferencesStore()
        val preferences = UserPreferences(store, DataMaintenanceGate())
        preferences.setHomeCardOrder(OLD_ORDER)
        preferences.setHomeCardVisibility(OLD_VISIBILITY)
        val vm = CardOrderViewModel(preferences)
        try {
            runCurrent()
            assertEquals("checkin", vm.orderedIds.first())
            vm.move(0, 1)
            vm.toggleVisible("trend")
            preferences.setHomeCardOrder(IMPORTED_ORDER)
            preferences.setHomeCardVisibility(IMPORTED_VISIBILITY)
            val writesBeforeSave = store.committedWrites

            val result = runCatching { vm.save() }

            assertTrue("The stale layout must be rejected", result.isFailure)
            assertEquals(IMPORTED_ORDER, preferences.homeCardOrder.first())
            assertEquals(IMPORTED_VISIBILITY, preferences.homeCardVisibility.first())
            assertEquals(writesBeforeSave, store.committedWrites)
            assertEquals("overview", vm.orderedIds.first())
            assertEquals(false, vm.visibility["trend"])
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun eachSaveCommitsBothFieldsOnceAndAdvancesItsOwnBaseline() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakePreferencesStore()
        val preferences = UserPreferences(store, DataMaintenanceGate())
        preferences.setHomeCardOrder(OLD_ORDER)
        preferences.setHomeCardVisibility(OLD_VISIBILITY)
        val vm = CardOrderViewModel(preferences)
        try {
            runCurrent()
            vm.move(0, 1)
            vm.toggleVisible("trend")
            var beforeSave = store.committedWrites
            vm.save()
            assertEquals("Order and visibility must commit atomically", beforeSave + 1, store.committedWrites)
            assertTrue(preferences.homeCardOrder.first().startsWith("[\"overview\""))
            assertTrue(preferences.homeCardVisibility.first().contains("\"trend\":false"))
            vm.move(1, 0)
            vm.toggleVisible("trend")
            beforeSave = store.committedWrites
            vm.save()
            assertEquals(beforeSave + 1, store.committedWrites)
            assertEquals(OLD_ORDER, preferences.homeCardOrder.first())
            assertTrue(preferences.homeCardVisibility.first().contains("\"trend\":true"))
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedLoadCannotSilentlyReportASuccessfulSaveOrOverwriteStoredLayout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakePreferencesStore()
        val preferences = UserPreferences(store, DataMaintenanceGate())
        preferences.setHomeCardOrder(IMPORTED_ORDER)
        preferences.setHomeCardVisibility(IMPORTED_VISIBILITY)
        store.failReads = true
        val vm = CardOrderViewModel(preferences)
        try {
            runCurrent()
            store.failReads = false
            val writesBeforeSave = store.committedWrites
            assertTrue(runCatching { vm.save() }.isFailure)
            assertEquals(writesBeforeSave, store.committedWrites)
            assertEquals(IMPORTED_ORDER, preferences.homeCardOrder.first())
            assertEquals(IMPORTED_VISIBILITY, preferences.homeCardVisibility.first())
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedWriteRetainsBothStoredFieldsAndCanRetryTheSameDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakePreferencesStore()
        val preferences = UserPreferences(store, DataMaintenanceGate())
        preferences.setHomeCardOrder(OLD_ORDER)
        preferences.setHomeCardVisibility(OLD_VISIBILITY)
        val vm = CardOrderViewModel(preferences)
        try {
            runCurrent()
            vm.move(0, 1)
            vm.toggleVisible("trend")
            store.writeFailure = IOException("disk unavailable")
            assertTrue(runCatching { vm.save() }.exceptionOrNull() is IOException)
            assertEquals(OLD_ORDER, preferences.homeCardOrder.first())
            assertEquals(OLD_VISIBILITY, preferences.homeCardVisibility.first())
            store.writeFailure = null
            vm.save()
            assertTrue(preferences.homeCardOrder.first().startsWith("[\"overview\""))
            assertTrue(preferences.homeCardVisibility.first().contains("\"trend\":false"))
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class FakePreferencesStore : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var failReads = false
        var writeFailure: Exception? = null
        var committedWrites = 0
            private set
        override val data: Flow<Preferences> = flow {
            if (failReads) throw IOException("disk read unavailable")
            emitAll(values)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            val result = transform(values.value)
            values.value = result
            committedWrites++
            return result
        }
    }

    private companion object {
        const val OLD_ORDER = "[\"checkin\",\"overview\",\"trend\",\"length\",\"achievement\"]"
        const val OLD_VISIBILITY = "{\"checkin\":true,\"overview\":true,\"trend\":true,\"length\":true,\"achievement\":true}"
        const val IMPORTED_ORDER = "[\"achievement\",\"trend\",\"overview\",\"length\",\"checkin\"]"
        const val IMPORTED_VISIBILITY = "{\"checkin\":false,\"overview\":true,\"trend\":false,\"length\":true,\"achievement\":true}"
    }
}