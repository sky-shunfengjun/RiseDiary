package com.risediary.app.data.backup

import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.DefaultVolumeMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DetailVideoBackupTest {
    @Test fun olderBackupWithoutVideoPrivacyFieldRestoresDisabled() {
        val old = BackupJsonCodec.settingsToJson(settings()).apply { remove("detail_video_hidden_by_default") }
        assertFalse(BackupJsonCodec.parseSettings(old.toString()).detailVideoHiddenByDefault)
    }

    @Test fun newBackupPreservesBothPrivacyValuesAndOtherPreferences() {
        for (hidden in listOf(false, true)) {
            val source = settings().copy(detailVideoHiddenByDefault = hidden)
            val encoded = BackupJsonCodec.settingsToJson(source)
            assertEquals(hidden, encoded.getBoolean("detail_video_hidden_by_default"))
            assertEquals(source, BackupJsonCodec.parseSettings(encoded.toString()))
        }
    }

    @Test fun malformedPrivacyFieldIsRejectedBeforeRestore() {
        for (invalid in listOf("true", 1, JSONObject.NULL)) {
            val encoded = BackupJsonCodec.settingsToJson(settings()).put("detail_video_hidden_by_default", invalid)
            assertThrows(IllegalArgumentException::class.java) { BackupJsonCodec.parseSettings(encoded.toString()) }
        }
    }

    private fun settings() = SettingsSnapshot("机长", 2f, DefaultVolumeMode.MILLILITERS, false, "22:00",
        false, 7, "22:00", false, 1, "22:00", true, true, false, BackgroundLockMode.ALWAYS,
        "system", "[]", "{}", false)
}
