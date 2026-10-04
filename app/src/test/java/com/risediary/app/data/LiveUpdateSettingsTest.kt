package com.risediary.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LiveUpdateSettingsTest {
    private val key = booleanPreferencesKey("live_updates_enabled")

    @Test fun oldSettingsRestoreEnablesLiveUpdatesByDefault() = runTest {
        val source = UserPreferences(Store(), DataMaintenanceGate())
        val destination = UserPreferences(Store(), DataMaintenanceGate())
        destination.applySettingsSnapshot(source.settingsSnapshot(source.rawSnapshot()))
        assertEquals(true, destination.rawSnapshot()[key])
    }

    @Test fun disabledSettingSurvivesRestoreIntoAnotherInstallation() = runTest {
        val source = UserPreferences(Store(preferencesOf(key to false)), DataMaintenanceGate())
        val destination = UserPreferences(Store(), DataMaintenanceGate())
        destination.applySettingsSnapshot(source.settingsSnapshot(source.rawSnapshot()))
        assertEquals(false, destination.rawSnapshot()[key])
    }

    @Test fun failedRestoreDoesNotOverwriteDisabledSetting() = runTest {
        val source = UserPreferences(Store(), DataMaintenanceGate())
        val store = Store(preferencesOf(key to false)).also { it.failure = IOException("full") }
        val destination = UserPreferences(store, DataMaintenanceGate())
        assertTrue(runCatching { destination.applySettingsSnapshot(source.settingsSnapshot(source.rawSnapshot())) }.isFailure)
        assertEquals(false, destination.rawSnapshot()[key])
    }


    @Test fun userTogglePersistsAndBackupReadsTheCommittedValue() = runTest {
        val prefs = UserPreferences(Store(), DataMaintenanceGate())
        assertTrue(prefs.liveUpdatesEnabled.first())
        prefs.setLiveUpdatesEnabled(false)
        assertFalse(prefs.liveUpdatesEnabled.first())
        assertFalse(prefs.settingsSnapshot(prefs.rawSnapshot()).liveUpdatesEnabled)
    }

    @Test fun failedTogglePreservesThePreviousSetting() = runTest {
        val store = Store(preferencesOf(key to false)).also { it.failure = IOException("full") }
        val prefs = UserPreferences(store, DataMaintenanceGate())
        assertTrue(runCatching { prefs.setLiveUpdatesEnabled(true) }.isFailure)
        assertFalse(prefs.liveUpdatesEnabled.first())
    }

    @Test fun unreadableSettingDoesNotSilentlyBecomeEnabled() = runTest {
        val data = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unreadable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("unused")
        }
        assertTrue(runCatching { UserPreferences(data, DataMaintenanceGate()).liveUpdatesEnabled.first() }.isFailure)
    }

    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        var failure: Exception? = null
        private val values = MutableStateFlow(initial)
        override val data: Flow<Preferences> = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            failure?.let { throw it }
            return transform(values.value).also { values.value = it }
        }
    }
}
