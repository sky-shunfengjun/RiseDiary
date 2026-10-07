package com.risediary.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DetailVideoSettingsTest {
    private val key = booleanPreferencesKey("detail_video_hidden_by_default")

    @Test fun enabledPrivacySettingSurvivesBackupRestore() = runTest {
        val source = UserPreferences(Store(preferencesOf(key to true)), DataMaintenanceGate())
        val destination = UserPreferences(Store(), DataMaintenanceGate())
        destination.applySettingsSnapshot(source.settingsSnapshot(source.rawSnapshot()))
        assertEquals(true, destination.rawSnapshot()[key])
    }

    @Test fun oldSettingsRestoreTurnsOffPreviousPrivacySetting() = runTest {
        val source = UserPreferences(Store(), DataMaintenanceGate())
        val destination = UserPreferences(Store(preferencesOf(key to true)), DataMaintenanceGate())
        destination.applySettingsSnapshot(source.settingsSnapshot(source.rawSnapshot()))
        assertEquals(false, destination.rawSnapshot()[key])
    }

    @Test fun togglePersistsIntoBackupWithoutChangingOtherSettings() = runTest {
        val prefs = UserPreferences(Store(), DataMaintenanceGate())
        assertFalse(prefs.detailVideoHiddenByDefault.first())
        prefs.setDetailVideoHiddenByDefault(true)
        val snapshot = prefs.settingsSnapshot(prefs.rawSnapshot())
        assertTrue(snapshot.detailVideoHiddenByDefault)
        assertTrue(snapshot.liveUpdatesEnabled)
        prefs.setDetailVideoHiddenByDefault(false)
        assertFalse(prefs.detailVideoHiddenByDefault.first())
    }

    @Test fun failedSaveKeepsPreviousSettingAndCanRetry() = runTest {
        val store = Store(preferencesOf(key to true)).also { it.failure = IOException("full") }
        val prefs = UserPreferences(store, DataMaintenanceGate())
        assertTrue(runCatching { prefs.setDetailVideoHiddenByDefault(false) }.isFailure)
        assertTrue(prefs.detailVideoHiddenByDefault.first())
        store.failure = null
        prefs.setDetailVideoHiddenByDefault(false)
        assertFalse(prefs.detailVideoHiddenByDefault.first())
    }

    @Test fun unreadablePrivacySettingDoesNotExposeVideoWithFalseDefault() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unreadable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("unused")
        }
        val prefs = UserPreferences(store, DataMaintenanceGate())
        assertTrue(runCatching { prefs.detailVideoHiddenByDefault.first() }.isFailure)
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
