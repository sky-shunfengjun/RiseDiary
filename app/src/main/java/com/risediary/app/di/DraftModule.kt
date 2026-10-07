package com.risediary.app.di

import com.risediary.app.data.draft.RecordDraftRepository
import com.risediary.app.data.draft.RoomRecordDraftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DraftModule {
    @Binds @Singleton
    abstract fun bindDrafts(implementation: RoomRecordDraftRepository): RecordDraftRepository
}
