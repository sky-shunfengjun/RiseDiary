package com.risediary.app.di

import com.risediary.app.update.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds abstract fun releaseSource(value: GitHubReleaseChecker): ReleaseSource
    @Binds abstract fun preferences(value: DiskUpdatePreferences): UpdatePreferences
    @Binds abstract fun downloads(value: SystemUpdateDownloads): UpdateDownloads
}
