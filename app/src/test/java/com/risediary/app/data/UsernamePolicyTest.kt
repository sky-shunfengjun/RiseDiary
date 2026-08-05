package com.risediary.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsernamePolicyTest {

    @Test
    fun trimsUsernameBeforeSaving() {
        assertEquals("机长甲", UsernamePolicy.normalize("  机长甲  "))
    }

    @Test
    fun blankUsernameFallsBackToDefault() {
        assertEquals(UsernamePolicy.DEFAULT_USERNAME, UsernamePolicy.normalize("   "))
    }

    @Test
    fun usernameIsLimitedToBackupMaximum() {
        val normalized = UsernamePolicy.normalize("甲".repeat(UsernamePolicy.MAX_LENGTH + 5))

        assertEquals(UsernamePolicy.MAX_LENGTH, normalized.length)
    }

    @Test
    fun emojiIsKeptWholeAndCountedAsOneCharacter() {
        val normalized = UsernamePolicy.normalize("甲".repeat(39) + "😀" + "多余")

        assertEquals("甲".repeat(39) + "😀", normalized)
        assertEquals(true, UsernamePolicy.isWithinLimit(normalized))
    }
}
