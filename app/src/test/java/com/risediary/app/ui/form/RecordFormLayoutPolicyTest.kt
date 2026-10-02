package com.risediary.app.ui.form

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFormLayoutPolicyTest {
    @Test
    fun keyboardOnlyRemovesBottomClearance() {
        assertEquals(68, recordFormBottomSpacerDp(imeVisible = false))
        assertEquals(0, recordFormBottomSpacerDp(imeVisible = true))

        val original = PaddingValues(start = 16.dp, top = 20.dp, end = 18.dp, bottom = 24.dp)
        val normal = recordFormContentPadding(original, imeVisible = false)
        val keyboard = recordFormContentPadding(original, imeVisible = true)
        assertEquals(24.dp, normal.calculateBottomPadding())
        assertEquals(0.dp, keyboard.calculateBottomPadding())
        for (padding in listOf(normal, keyboard)) {
            assertEquals(16.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
            assertEquals(20.dp, padding.calculateTopPadding())
            assertEquals(18.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        }
    }
}
