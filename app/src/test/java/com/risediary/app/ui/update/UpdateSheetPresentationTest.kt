package com.risediary.app.ui.update

import org.junit.Assert.*
import org.junit.Test

/** Regression cases for the backdrop; deliberately not executed in this uncompiled session. */
class UpdateSheetPresentationTest {
    @Test fun dismissKeepsBackdropUntilTheSheetFinishesExiting() {
        val state = openedSheet()
        state.updateVisibility(false)
        assertFalse(state.visible)
        assertTrue(state.retained)
        state.finishDismiss()
        assertFalse(state.retained)
    }

    @Test fun oldDismissCompletionCannotClearAReopenedSheetBackdrop() {
        val state = openedSheet()
        state.onBackProgress(0.7f, closesSheet = true)
        state.onBackIdle()
        state.updateVisibility(false)
        state.updateVisibility(true)
        state.finishDismiss()
        assertTrue(state.visible)
        assertTrue(state.retained)
        assertStrength(state, 1f)
        assertNull(state.settlementId)
    }

    @Test fun reopeningStartsFromTheCurrentFadeWithoutReusingTheOldGesture() {
        val state = openedSheet()
        state.onBackProgress(0.75f, closesSheet = true)
        state.onBackIdle()
        val previousOpening = state.openingId
        state.updateVisibility(false)
        state.updateVisibility(true)
        assertEquals(0.25f, state.openingMultiplier, 0.001f)
        assertTrue(state.openingId > previousOpening)
        assertStrength(state, 1f)
        assertNull(state.settlementId)
        state.onBackProgress(0.5f, closesSheet = true)
        assertStrength(state, 0.5f)
    }
    @Test fun consecutiveReopensBeforeTheNextFramePreserveTheAlreadyFadedStrength() {
        val state = openedSheet()
        state.onBackProgress(0.75f, closesSheet = true)
        state.updateVisibility(false)
        state.updateVisibility(true)
        state.updateVisibility(false)
        state.updateVisibility(true)
        assertEquals(0.25f, state.openingMultiplier, 0.001f)
    }

    @Test fun anAppliedOpeningDoesNotFoldItsPreviousMultiplierIntoTheNextOpeningAgain() {
        val state = openedSheet()
        state.onBackProgress(0.75f, closesSheet = true)
        state.onBackIdle()
        state.updateVisibility(false)
        state.updateVisibility(true)
        state.onOpeningApplied(state.openingId)
        state.onBackProgress(0.5f, closesSheet = true)
        state.onBackIdle()
        state.updateVisibility(false)
        state.updateVisibility(true)
        assertEquals(0.5f, state.openingMultiplier, 0.001f)
    }
    @Test fun previewFollowsProgressAndItsReversalWithoutAnotherAnimation() {
        val state = openedSheet()
        state.onBackProgress(0f, closesSheet = true)
        assertStrength(state, 1f)
        state.onBackProgress(0.75f, closesSheet = true)
        assertStrength(state, 0.25f)
        state.onBackProgress(0.25f, closesSheet = true)
        assertStrength(state, 0.75f)
        state.onBackProgress(1f, closesSheet = true)
        assertStrength(state, 0f)
    }

    @Test fun outOfRangeProgressIsClampedAndNonFiniteProgressCannotPoisonTheEffect() {
        val state = openedSheet()
        state.onBackProgress(-0.5f, closesSheet = true)
        assertStrength(state, 1f)
        state.onBackProgress(2f, closesSheet = true)
        assertStrength(state, 0f)
        state.onBackProgress(0.5f, closesSheet = true)
        state.onBackProgress(Float.NaN, closesSheet = true)
        state.onBackProgress(Float.POSITIVE_INFINITY, closesSheet = true)
        assertStrength(state, 0.5f)
    }

    @Test fun returningInsideTheSheetDoesNotFadeItsBackdropEvenIfThePageChanges() {
        val state = openedSheet()
        state.onBackProgress(0.2f, closesSheet = false)
        state.onBackProgress(0.9f, closesSheet = true)
        state.onSheetBounds(top = 920, height = 800, windowHeight = 1000)
        state.onBackIdle()
        assertStrength(state, 1f)
        assertNull(state.settlementId)
        // A subsequent gesture that really closes the sheet can now preview normally.
        state.onBackProgress(0.4f, closesSheet = true)
        assertStrength(state, 0.6f)
    }

    @Test fun releasingTheGestureKeepsItsCurrentStrengthUntilTheNativeSheetMoves() {
        val state = openedSheet()
        state.onBackProgress(0.5f, closesSheet = true)
        state.onSheetBounds(top = 600, height = 800, windowHeight = 1000)
        state.onBackIdle()
        assertStrength(state, 0.5f)
        state.onSheetBounds(top = 600, height = 800, windowHeight = 1000)
        assertStrength(state, 0.5f)
    }

    @Test fun cancelledPreviewRestoresStrengthWithTheNativeRebound() {
        val state = openedSheet()
        state.onBackProgress(0.5f, closesSheet = true)
        state.onSheetBounds(top = 600, height = 800, windowHeight = 1000)
        state.onBackIdle()
        state.onSheetBounds(top = 400, height = 800, windowHeight = 1000)
        assertStrength(state, 0.75f)
        state.onSheetBounds(top = 200, height = 800, windowHeight = 1000)
        assertStrength(state, 1f)
        assertNull(state.settlementId)
        assertTrue(state.visible)
    }

    @Test fun confirmedPreviewContinuesToZeroAndDoesNotReblurBeforeDismissalCompletes() {
        val state = openedSheet()
        state.onBackProgress(0.5f, closesSheet = true)
        state.onSheetBounds(top = 600, height = 800, windowHeight = 1000)
        state.onBackIdle()
        state.onSheetBounds(top = 800, height = 800, windowHeight = 1000)
        assertStrength(state, 0.25f)
        state.onSheetBounds(top = 1000, height = 800, windowHeight = 1000)
        assertStrength(state, 0f)
        state.updateVisibility(false)
        state.onBackIdle()
        assertStrength(state, 0f)
        assertTrue(state.retained)
        state.finishDismiss()
        assertFalse(state.retained)
        assertStrength(state, 0f)
    }

    @Test fun aLaggingLayoutDoesNotJumpWhenHandingOffFromProgressToSheetMotion() {
        val state = openedSheet()
        state.onBackProgress(0.5f, closesSheet = true)
        state.onSheetBounds(top = 520, height = 800, windowHeight = 1000)
        state.onBackIdle()
        state.onSheetBounds(top = 760, height = 800, windowHeight = 1000)
        assertStrength(state, 0.25f)
        state.onSheetBounds(top = 1000, height = 800, windowHeight = 1000)
        assertStrength(state, 0f)
    }

    @Test fun cancellationBeforeTheFirstMovingLayoutCannotLeaveResidualBlurReduction() {
        val state = openedSheet()
        state.onBackProgress(0.4f, closesSheet = true)
        state.onBackIdle()
        val id = requireNotNull(state.settlementId)
        // Idle alone is ambiguous: hold the preview until the native animation clock finishes.
        state.onSheetBounds(top = 200, height = 800, windowHeight = 1000)
        assertStrength(state, 0.6f)
        state.finishBackSettlement(id)
        assertStrength(state, 1f)
        assertNull(state.settlementId)
    }

    @Test fun anOffscreenFirstLayoutCompletesTheFadeEvenIfIntermediateFramesWereSkipped() {
        val state = openedSheet()
        state.onBackProgress(0.4f, closesSheet = true)
        state.onBackIdle()
        state.onSheetBounds(top = 1000, height = 800, windowHeight = 1000)
        assertStrength(state, 0f)
    }
    @Test fun aLateSettlementCompletionCannotChangeAReopenedSheet() {
        val state = openedSheet()
        state.onBackProgress(0.6f, closesSheet = true)
        state.onBackIdle()
        val oldId = requireNotNull(state.settlementId)
        state.updateVisibility(false)
        state.updateVisibility(true)
        state.onBackProgress(0.3f, closesSheet = true)
        state.onBackIdle()
        state.finishBackSettlement(oldId)
        assertStrength(state, 0.7f)
        assertNotEquals(oldId, state.settlementId)
    }

    @Test fun windowResizeClearsTheOldPreviewAndIgnoresTheRestOfThatGesture() {
        val state = openedSheet()
        state.onBackProgress(0.7f, closesSheet = true)
        state.onSheetBounds(top = 300, height = 600, windowHeight = 900)
        state.onBackProgress(0.9f, closesSheet = true)
        assertStrength(state, 1f)
        state.onBackIdle()
        state.onBackProgress(0.2f, closesSheet = true)
        assertStrength(state, 0.8f)
    }

    @Test fun reopeningDuringAnOldGestureIgnoresItsRemainingEventsUntilIdle() {
        val state = openedSheet()
        state.onBackProgress(0.6f, closesSheet = true)
        state.updateVisibility(false)
        state.clearBackPreview()
        state.updateVisibility(true)
        state.onBackProgress(0.9f, closesSheet = true)
        assertStrength(state, 1f)
        state.onBackIdle()
        state.onBackProgress(0.25f, closesSheet = true)
        assertStrength(state, 0.75f)
    }

    @Test fun aHiddenSheetsObserverCannotReblurWhileAnotherPageHandlesBack() {
        val state = openedSheet()
        state.onBackProgress(1f, closesSheet = true)
        state.onBackIdle()
        state.updateVisibility(false)
        state.onBackProgress(0.5f, closesSheet = false)
        state.onBackIdle()
        assertStrength(state, 0f)
    }
    @Test fun blockingOrDisposingTheHostReleasesOldGestureState() {
        val state = openedSheet()
        state.onBackProgress(0.6f, closesSheet = true)
        state.clearBackPreview()
        assertStrength(state, 1f)
        assertNull(state.settlementId)
        state.updateVisibility(false)
        state.reset()
        assertFalse(state.visible)
        assertFalse(state.retained)
        assertStrength(state, 1f)
    }

    private fun openedSheet() = UpdateSheetPresentation().also {
        it.updateVisibility(true)
        it.onSheetBounds(top = 200, height = 800, windowHeight = 1000)
    }

    private fun assertStrength(state: UpdateSheetPresentation, expected: Float) {
        assertEquals(expected, state.backdropMultiplier, 0.001f)
    }
}
