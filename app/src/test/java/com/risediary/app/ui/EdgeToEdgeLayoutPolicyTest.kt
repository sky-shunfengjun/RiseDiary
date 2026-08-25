package com.risediary.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.form.recordFormContentPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeToEdgeLayoutPolicyTest {

    @Test
    fun main_page_keeps_the_page_root_full_screen() {
        assertFalse(mainPageAppliesSystemBarsToRoot())
    }

    @Test
    fun overlay_insets_are_applied_inside_scrollable_content() {
        assertTrue(scrollInsetsBelongInsideScrollableContent())
    }

    @Test
    fun record_form_does_not_keep_manual_bottom_padding_while_ime_is_visible() {
        assertEquals(
            0.dp,
            recordFormContentPadding(
                PaddingValues(bottom = 24.dp),
                imeVisible = true
            ).calculateBottomPadding()
        )
        assertEquals(
            24.dp,
            recordFormContentPadding(
                PaddingValues(bottom = 24.dp),
                imeVisible = false
            ).calculateBottomPadding()
        )
    }
}
