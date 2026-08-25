package com.risediary.app.ui.about

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.unit.dp

class ThirdPartyLibLayoutPolicyTest {

    @Test
    fun library_card_uses_miuix_default_corner_radius() {
        assertEquals(16f, thirdPartyLibCardCornerRadius.value, 0.0001f)
    }

    @Test
    fun libraries_entry_does_not_add_extra_horizontal_padding() {
        assertEquals(0.dp, aboutLibrariesEntryHorizontalPadding())
    }
}
