package com.risediary.app.update

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePreferencePolicyTest {
    @Test fun defaultsAndUnknownChannelRemainCompatible() {
        assertEquals(UpdateSettings(), emptyPreferences().updateSettings())
        val prefs = mutablePreferencesOf(RELEASE_CHANNEL to "unknown", CHANNEL to "unknown")
        assertEquals(ReleaseChannel.STABLE, prefs.updateSettings().releaseChannel)
        assertEquals(UpdateChannel.OFFICIAL, prefs.updateSettings().channel)
    }
    @Test fun resetChangesAllThreeDebugKeysAndRetainsEveryOtherPreference() {
        val other = stringPreferencesKey("unrelated")
        val prefs = mutablePreferencesOf(AUTOMATIC to false, CHANNEL to UpdateChannel.PROXY_7ED.name,
            FORCE to true, RELEASE_CHANNEL to ReleaseChannel.PREVIEW.name, DEVELOPER to true,
            RECORD to "existing task", other to "retained")
        prefs.restoreDeveloperSettings()
        val settings = prefs.updateSettings()
        assertFalse(settings.forceCheck)
        assertFalse(settings.developerEnabled)
        assertEquals(ReleaseChannel.STABLE, settings.releaseChannel)
        assertFalse(settings.automaticCheck)
        assertEquals(UpdateChannel.PROXY_7ED, settings.channel)
        assertEquals("existing task",prefs[RECORD])
        assertEquals("retained",prefs[other])
    }
}