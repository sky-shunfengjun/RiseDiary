package com.risediary.app.ui.video

import org.junit.Assert.*
import org.junit.Test

class FullscreenEntryPolicyTest {
    @Test fun landscapeVideoWaitsForMatchingConfigurationBeforeShowingFullscreen() {
        val waiting = beginFullscreenEntry(VideoOrientation.LANDSCAPE, VideoOrientation.PORTRAIT)
        assertEquals(FullscreenEntryPhase.WAITING_FOR_ROTATION, waiting)
        assertEquals(waiting, completeFullscreenEntry(waiting, VideoOrientation.LANDSCAPE, VideoOrientation.PORTRAIT))
        assertEquals(FullscreenEntryPhase.OPEN, completeFullscreenEntry(waiting, VideoOrientation.LANDSCAPE, VideoOrientation.LANDSCAPE))
    }
    @Test fun sameOrUnknownWindowOrientationDoesNotWait() {
        assertEquals(FullscreenEntryPhase.OPEN, beginFullscreenEntry(VideoOrientation.PORTRAIT, VideoOrientation.PORTRAIT))
        assertEquals(FullscreenEntryPhase.OPEN, beginFullscreenEntry(VideoOrientation.LANDSCAPE, null))
    }
    @Test fun ignoredRotationHasBoundedFallback() {
        val waiting = beginFullscreenEntry(VideoOrientation.PORTRAIT, VideoOrientation.LANDSCAPE)
        assertEquals(FullscreenEntryPhase.OPEN, completeFullscreenEntry(waiting, VideoOrientation.PORTRAIT, VideoOrientation.LANDSCAPE, timedOut = true))
    }
    @Test fun cancelledEntryCannotReopenOnLateConfigurationOrTimeout() {
        assertEquals(FullscreenEntryPhase.CLOSED, completeFullscreenEntry(FullscreenEntryPhase.CLOSED,
            VideoOrientation.LANDSCAPE, VideoOrientation.LANDSCAPE))
        assertEquals(FullscreenEntryPhase.CLOSED, completeFullscreenEntry(FullscreenEntryPhase.CLOSED,
            VideoOrientation.LANDSCAPE, VideoOrientation.PORTRAIT, timedOut = true))
    }
    @Test fun openEntryDoesNotWaitAgainOnManualRotation() {
        assertEquals(FullscreenEntryPhase.OPEN, completeFullscreenEntry(FullscreenEntryPhase.OPEN,
            VideoOrientation.LANDSCAPE, VideoOrientation.PORTRAIT))
    }
}
