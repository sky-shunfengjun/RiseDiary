package com.risediary.app.ui.components

import org.junit.Assert.*
import org.junit.Test

class RiseUiPolicyTest {
    @Test fun secondaryDangerLabelFollowsTheThemeErrorColor() {
        for (dark in listOf(false, true)) {
            val error = if (dark) 0xFFFF7373 else 0xFFEF5350
            val palette = dialogActionPalette(dark, false, true, true, error)
            assertEquals(error, palette.foreground)
            assertEquals(if (dark) 0xFF303238 else 0xFFFFFFFF, palette.background)
        }
    }

    @Test fun emphasizedButtonsFollowTheSuppliedThemeInsteadOfADeeperFixedShade() {
        for (dark in listOf(false, true)) {
            val primary = if (dark) 0xFF0091FF else 0xFF0088FF
            val error = if (dark) 0xFFFF7373 else 0xFFEF5350
            val normal = dialogActionPalette(dark, true, false, true, error, primaryColor = primary)
            val danger = dialogActionPalette(dark, true, true, true, error, primaryColor = primary)
            assertEquals(primary, normal.background)
            assertEquals(error, danger.background)
            assertEquals(0xFFFFFFFF, normal.foreground)
            assertEquals(0xFFFFFFFF, danger.foreground)
        }
    }

    @Test fun disabledDialogActionCannotDispatchItsCallback() {
        var calls = 0
        RiseDialogAction("保存", { calls++ }, DialogActionTone.Normal, enabled = false).perform()
        assertEquals(0, calls)
        RiseDialogAction("保存", { calls++ }, DialogActionTone.Normal).perform()
        assertEquals(1, calls)
    }

    @Test fun secondaryButtonsRemainOpaqueAndTheirLabelsDifferFromTheirBackgrounds() {
        for (dark in listOf(false, true)) for (enabled in listOf(false, true)) {
            val colors = dialogActionPalette(dark, emphasized = false, destructive = false, enabled, 0xFFCF3030)
            assertEquals(255L, colors.background ushr 24)
            assertNotEquals(colors.background, colors.foreground)
        }
    }

    @Test fun destructiveAndNormalConfirmationsHaveDifferentSurfacesAndLightLabels() {
        val normal = dialogActionPalette(false, true, false, true, 0xFFCF3030)
        val danger = dialogActionPalette(false, true, true, true, 0xFFCF3030)
        assertNotEquals(normal.background, danger.background)
        assertEquals(0xFFFFFFFF, normal.foreground)
        assertEquals(0xFFFFFFFF, danger.foreground)
        val secondaryDanger = dialogActionPalette(false, false, true, true, 0xFFCF3030)
        assertEquals(0xFFFFFFFF, secondaryDanger.background)
        assertEquals(0xFFCF3030, secondaryDanger.foreground)
    }

    @Test fun overflowReservesAMenuSlotAndDoesNotLoseOrDuplicateActions() {
        val layout = topActionPlacement(actionCount = 4, availableSlots = 2)
        assertEquals(listOf(0), layout.directIndices)
        assertEquals(listOf(1, 2, 3), layout.overflowIndices)
        val narrow = topActionPlacement(actionCount = 3, availableSlots = 1)
        assertTrue(narrow.directIndices.isEmpty())
        assertEquals(listOf(0, 1, 2), narrow.overflowIndices)
    }

    @Test fun actionsThatFitDoNotCreateAnUnnecessaryMenu() {
        assertEquals(listOf(0, 1), topActionPlacement(2, 2).directIndices)
        assertTrue(topActionPlacement(2, 2).overflowIndices.isEmpty())
        assertTrue(topActionPlacement(0, 2).directIndices.isEmpty())
        assertTrue(topActionPlacement(0, 2).overflowIndices.isEmpty())
    }
}
