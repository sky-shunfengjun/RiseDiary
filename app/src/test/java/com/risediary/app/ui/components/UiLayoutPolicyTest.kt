package com.risediary.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLayoutPolicyTest {
    @Test fun largeTextHasRoomInsideActions() {
        assertTrue(uiActionHeightDp(2f) >= 72f)
        assertTrue(uiActionHeightDp(1f) >= 48f)
    }

    @Test fun narrowDialogsDoNotSqueezeTwoActionsTogether() {
        assertTrue(dialogActionsShouldStack(200f, 1f))
        assertTrue(dialogActionsShouldStack(300f, 1.8f))
        assertFalse(dialogActionsShouldStack(300f, 1f))
    }

    @Test fun tallDocksReserveEnoughContentRoom() {
        assertTrue(bottomActionPaddingDp(150f) >= 174f)
        assertTrue(bottomActionPaddingDp(58f) >= 82f)
    }

    @Test fun invalidMeasurementsNeverProduceInfiniteConstraints() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -20f).forEach {
            assertTrue(uiActionHeightDp(it).isFinite())
            assertTrue(uiActionHeightDp(it) >= 48f)
            assertTrue(bottomActionPaddingDp(it).isFinite())
            assertTrue(bottomActionPaddingDp(it) >= 104f)
        }
    }
}
