package com.risediary.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UpdateIntroCompletionTest {
    private val seen = stringPreferencesKey("last_completed_update_intro_id")
    private val first = booleanPreferencesKey("onboarding_completed")

    @Test fun finishingFirstRunAtomicallySuppressesTheSecondGuide() = runTest {
        val store = Store()
        val prefs = UserPreferences(store, DataMaintenanceGate())
        prefs.finishOnboarding(true, null)
        assertEquals(true, store.data.first()[first])
        assertEquals("2.0.0", store.data.first()[seen])
        assertEquals(1, store.writes)
    }
    @Test fun failedFirstRunCannotLeaveEitherCompletionMarkerCommitted() = runTest {
        val store = Store().also { it.fail = true }
        assertTrue(runCatching { UserPreferences(store, DataMaintenanceGate()).finishOnboarding(true, null) }.isFailure)
        assertNull(store.data.first()[first])
        assertNull(store.data.first()[seen])
    }
    @Test fun reviewingFirstRunDoesNotSuppressAnUnseenUpdateGuide() = runTest {
        val store = Store(preferencesOf(first to true))
        UserPreferences(store, DataMaintenanceGate()).finishOnboarding(false, null)
        assertNull(store.data.first()[seen])
    }
    @Test fun restoringUserSettingsPreservesDeviceLocalCompletion() = runTest {
        val source = UserPreferences(Store(), DataMaintenanceGate())
        val target = UserPreferences(Store(preferencesOf(seen to "2.0.0")), DataMaintenanceGate())
        target.applySettingsSnapshot(source.settingsSnapshot(source.rawSnapshot()))
        assertEquals("2.0.0", target.rawSnapshot()[seen])
    }
    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val values = MutableStateFlow(initial)
        override val data: Flow<Preferences> = values
        var fail = false
        var writes = 0
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) throw IOException("full")
            return transform(values.value).also { values.value = it; writes++ }
        }
    }
}
