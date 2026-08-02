package com.risediary.app.di

import android.content.Context
import androidx.room.Room
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.dao.AchievementDao
import com.risediary.app.data.dao.FlightDao
import com.risediary.app.data.dao.LengthRecordDao
import com.risediary.app.data.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "risediary.db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3
            )
            .build()

    @Provides
    fun provideFlightDao(database: AppDatabase): FlightDao = database.flightDao()

    @Provides
    fun provideLengthRecordDao(database: AppDatabase): LengthRecordDao =
        database.lengthRecordDao()

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao = database.tagDao()

    @Provides
    fun provideAchievementDao(database: AppDatabase): AchievementDao =
        database.achievementDao()

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun provideClock(zoneId: ZoneId): Clock = Clock.system(zoneId)
}
