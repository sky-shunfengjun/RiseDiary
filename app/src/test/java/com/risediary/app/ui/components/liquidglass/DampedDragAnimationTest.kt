package com.risediary.app.ui.components.liquidglass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DampedDragAnimationTest {
    @Test
    fun toggleKeepsInitialDownForClickAndConsumesMovement() {
        assertFalse(
            shouldConsumeDragChange(
                consumeDragChanges = true,
                consumeInitialDown = false,
                hasMovement = false
            )
        )
        assertTrue(
            shouldConsumeDragChange(
                consumeDragChanges = true,
                consumeInitialDown = false,
                hasMovement = true
            )
        )
    }

    @Test
    fun existingConsumersCanStillConsumeInitialDown() {
        assertTrue(
            shouldConsumeDragChange(
                consumeDragChanges = true,
                consumeInitialDown = true,
                hasMovement = false
            )
        )
        assertFalse(
            shouldConsumeDragChange(
                consumeDragChanges = false,
                consumeInitialDown = true,
                hasMovement = true
            )
        )
    }
}
