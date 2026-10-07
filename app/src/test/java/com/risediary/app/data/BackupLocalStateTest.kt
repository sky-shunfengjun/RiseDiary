package com.risediary.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BackupLocalStateTest {
    @Test fun restoringOrdinarySettingsCannotResetLocalOnboardingOrLock() = runTest {
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val preferences = UserPreferences(store, DataMaintenanceGate())
        preferences.setOnboardingCompleted(true)
        preferences.setAppLock(true, "1234")
        val imported = preferences.settingsSnapshot(preferences.rawSnapshot()).copy(
            username = "备份称呼", onboardingCompleted = false, predictionMaxTicks = 120)
        preferences.applySettingsForMaintenance(imported)
        assertTrue(preferences.securitySettings.first().onboardingCompleted)
        assertTrue(preferences.securitySettings.first().lockEnabled)
        assertEquals("1234", preferences.securitySettings.first().credential)
        assertEquals("备份称呼", preferences.settingsSnapshot(preferences.rawSnapshot()).username)
        assertEquals(120, preferences.settingsSnapshot(preferences.rawSnapshot()).predictionMaxTicks)
    }
}
