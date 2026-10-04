package com.risediary.app.ui.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLoadingPresentationTest {
    @Test fun openingEmptyVideoTimerDoesNotFlashLoadingIndicator() {
        assertFalse(showVideoLoadingIndicator(true, true, false, false, false))
    }
    @Test fun loadingAttachmentOrRecordStillShowsProgress() {
        assertTrue(showVideoLoadingIndicator(true, false, false, false, false))
    }
    @Test fun firstVideoSelectionKeepsItsRealLoadingState() {
        assertTrue(showVideoLoadingIndicator(true, true, false, false, true))
    }
    @Test fun restoringStartedVideoTimerStillShowsProgress() {
        assertTrue(showVideoLoadingIndicator(true, true, false, true, false))
    }
    @Test fun changingExistingVideoKeepsItsRealLoadingState() {
        assertTrue(showVideoLoadingIndicator(true, true, true, false, true))
    }
    @Test fun readyPageNeverShowsIndicator() {
        assertFalse(showVideoLoadingIndicator(false, true, true, true, true))
    }
}
