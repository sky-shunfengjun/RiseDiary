package com.risediary.app.ui.form

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFormLayoutPolicyTest {

    @Test
    fun bottomSpacerOnlyReservesTheSaveButtonWhenImeIsHidden() {
        assertEquals(48, recordFormBottomSpacerDp(imeVisible = false))
        assertEquals(0, recordFormBottomSpacerDp(imeVisible = true))
    }
}
