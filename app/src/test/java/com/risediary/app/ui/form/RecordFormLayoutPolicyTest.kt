package com.risediary.app.ui.form

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFormLayoutPolicyTest {

    @Test
    fun bottomSpacerPreservesTheExistingTailClearanceWhenImeIsHidden() {
        assertEquals(68, recordFormBottomSpacerDp(imeVisible = false))
        assertEquals(0, recordFormBottomSpacerDp(imeVisible = true))
    }
}
