package com.risediary.app.ui.video

internal enum class FullscreenEntryPhase { CLOSED, WAITING_FOR_ROTATION, OPEN }

internal fun beginFullscreenEntry(target: VideoOrientation, actual: VideoOrientation?): FullscreenEntryPhase =
    if (actual == null || target == actual) FullscreenEntryPhase.OPEN
    else FullscreenEntryPhase.WAITING_FOR_ROTATION

internal fun completeFullscreenEntry(
    phase: FullscreenEntryPhase, target: VideoOrientation, actual: VideoOrientation?, timedOut: Boolean = false
): FullscreenEntryPhase =
    if (phase == FullscreenEntryPhase.WAITING_FOR_ROTATION && (timedOut || target == actual))
        FullscreenEntryPhase.OPEN else phase
