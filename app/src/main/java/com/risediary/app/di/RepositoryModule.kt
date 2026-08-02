package com.risediary.app.di

import com.risediary.app.data.repository.AchievementRepository
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.data.repository.RoomAchievementRepository
import com.risediary.app.data.repository.RoomFlightRepository
import com.risediary.app.data.repository.RoomLengthRecordRepository
import com.risediary.app.data.repository.RoomTagRepository
import com.risediary.app.data.repository.TagRepository
import com.risediary.app.service.ElapsedRealtimeClock
import com.risediary.app.service.ServiceTimerController
import com.risediary.app.service.SystemElapsedRealtimeClock
import com.risediary.app.service.TimerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindFlightRepository(implementation: RoomFlightRepository): FlightRepository

    @Binds
    @Singleton
    abstract fun bindLengthRepository(
        implementation: RoomLengthRecordRepository
    ): LengthRecordRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(implementation: RoomTagRepository): TagRepository

    @Binds
    @Singleton
    abstract fun bindAchievementRepository(
        implementation: RoomAchievementRepository
    ): AchievementRepository

    @Binds
    @Singleton
    abstract fun bindTimerController(implementation: ServiceTimerController): TimerController

    @Binds
    @Singleton
    abstract fun bindElapsedRealtimeClock(
        implementation: SystemElapsedRealtimeClock
    ): ElapsedRealtimeClock
}
