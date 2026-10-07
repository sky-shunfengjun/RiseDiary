package com.risediary.app.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: VideoResourceCoordinator,
    private val diagnostics: VideoDiagnostics
) {
    fun create() = Media3VideoPlayerController(context, coordinator, diagnostics, initiallyActive = false)
}
