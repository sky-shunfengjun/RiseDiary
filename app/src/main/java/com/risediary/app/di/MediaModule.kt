package com.risediary.app.di

import com.risediary.app.media.AndroidVideoFileAccess
import com.risediary.app.media.VideoFileAccess
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds abstract fun videoFileAccess(implementation: AndroidVideoFileAccess): VideoFileAccess
}
