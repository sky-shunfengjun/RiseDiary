package com.risediary.app.ui.video

/** An empty video timer can show its normal placeholder while its timer state is read. */
internal fun showVideoLoadingIndicator(
    loading: Boolean, timerMode: Boolean, hasVideo: Boolean,
    started: Boolean, selectingVideo: Boolean
): Boolean = loading && (!timerMode || hasVideo || started || selectingVideo)
