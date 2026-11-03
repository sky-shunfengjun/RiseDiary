package com.risediary.app.ui.records

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.data.UserPreferences
import com.risediary.app.media.Media3VideoPlayerController
import com.risediary.app.media.VideoFileAccess
import com.risediary.app.media.VideoGrantRegistry
import com.risediary.app.media.libraryMaterialId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DetailVideoViewModel @Inject constructor(
    factory: com.risediary.app.media.VideoPlayerFactory,
    preferences: UserPreferences,
    files: VideoFileAccess,
    private val grants: VideoGrantRegistry,
    private val libraryUsage: com.risediary.app.data.library.LibraryUsageRegistry
) : ViewModel() {
    private val owner = "detail-player:" + UUID.randomUUID()
    val controller = factory.create()
    internal val session = DetailVideoSession(viewModelScope, preferences.detailVideoHiddenByDefault, { video ->
        val material = video.libraryMaterialId()
        if (material != null) check(libraryUsage.hold(owner, material)) { "素材正在删除" }
        else { libraryUsage.release(owner); grants.retain(owner, setOf(video.uriString)) }
        files.check(video)
    }, controller)
    internal val state = session.state
    override fun onCleared() { controller.release(); grants.forget(owner); libraryUsage.release(owner) }
}
