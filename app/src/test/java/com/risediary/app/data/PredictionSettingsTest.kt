package com.risediary.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PredictionSettingsTest {
    @Test fun defaultsToEightMillilitersAndIgnoresObsoleteConversion() = runTest {
        val prefs = UserPreferences(Store(preferencesOf(floatPreferencesKey("ml_per_spurt") to Float.NaN)), DataMaintenanceGate())
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
    }
    @Test fun obsoleteConversionFieldWithWrongTypeCannotBlockPrediction() = runTest {
        val prefs = UserPreferences(Store(preferencesOf(stringPreferencesKey("ml_per_spurt") to "obsolete")), DataMaintenanceGate())
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
    }
    @Test fun failedSettingsWritePreservesPreviousMaximum() = runTest {
        val store = Store().also { it.writeFailure = IOException("full") }
        val prefs = UserPreferences(store, DataMaintenanceGate())
        assertTrue(runCatching { prefs.setPredictionMaxTicks(123) }.isFailure)
        assertEquals(80, prefs.quantitySettings.first().predictionMaxTicks)
    }
    @Test fun settingAcceptsFifteenMillilitersButRejectsValuesBeyondSliderRange() = runTest {
        val prefs = UserPreferences(Store(), DataMaintenanceGate())
        prefs.setPredictionMaxTicks(1)
        assertEquals(1, prefs.predictionMaxTicks.first())
        prefs.setPredictionMaxTicks(150)
        listOf(0, -1, 151, 10_000).forEach {
            assertTrue(runCatching { prefs.setPredictionMaxTicks(it) }.isFailure)
            assertEquals(150, prefs.predictionMaxTicks.first())
        }
    }
    @Test fun previousLargerSettingUsesFifteenForNewFormsAndBackupWithoutChangingRecords() = runTest {
        val prefs = UserPreferences(Store(preferencesOf(intPreferencesKey("prediction_max_ticks") to 200)), DataMaintenanceGate())
        assertEquals(150, prefs.quantitySettings.first().predictionMaxTicks)
        assertEquals(150, prefs.settingsSnapshot(prefs.rawSnapshot()).predictionMaxTicks)
    }
    @Test fun restoringPreviousLargerSettingAppliesNewLimit() = runTest {
        val prefs = UserPreferences(Store(), DataMaintenanceGate())
        prefs.applySettingsSnapshot(prefs.settingsSnapshot(prefs.rawSnapshot()).copy(predictionMaxTicks = 200))
        assertEquals(150, prefs.predictionMaxTicks.first())
        assertEquals(150, prefs.rawSnapshot()[intPreferencesKey("prediction_max_ticks")])
    }
    @Test fun storedMaximumRoundTripsAndIsIncludedInBackupSettings() = runTest {
        val prefs = UserPreferences(Store(), DataMaintenanceGate())
        prefs.setPredictionMaxTicks(123)
        assertEquals(123, prefs.quantitySettings.first().predictionMaxTicks)
        assertEquals(123, prefs.settingsSnapshot(prefs.rawSnapshot()).predictionMaxTicks)
        assertTrue(runCatching { prefs.setPredictionMaxTicks(0) }.isFailure)
        assertEquals(123, prefs.quantitySettings.first().predictionMaxTicks)
    }
    @Test fun corruptMaximumDoesNotSilentlyBecomeDefault() = runTest {
        val prefs = UserPreferences(Store(preferencesOf(intPreferencesKey("prediction_max_ticks") to 0)), DataMaintenanceGate())
        assertTrue(runCatching { prefs.quantitySettings.first() }.isFailure)
    }
    @Test fun failedReadDoesNotInventAUsableDefault() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unreadable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("unused")
        }
        assertTrue(runCatching { UserPreferences(store, DataMaintenanceGate()).quantitySettings.first() }.isFailure)
    }
    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        var writeFailure: Exception? = null
        private val values = MutableStateFlow(initial)
        override val data: Flow<Preferences> = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            return transform(values.value).also { values.value = it }
        }
    }
}
