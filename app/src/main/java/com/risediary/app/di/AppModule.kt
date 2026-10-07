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
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8
            )
            .addCallback(com.risediary.app.data.UnsubmittedFormCleanup)
            .build()

    @Provides
    fun provideReadiness(database: AppDatabase): com.risediary.app.data.DatabaseReadiness =
        com.risediary.app.data.DatabaseReadiness {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Forces migration + schema validation and checks every user table before MAIN.
                database.openHelper.writableDatabase.query(
                    "SELECT (SELECT COUNT(*) FROM flights), (SELECT COUNT(*) FROM length_records), (SELECT COUNT(*) FROM tags), (SELECT COUNT(*) FROM achievements)"
                ).use { check(it.moveToFirst()) { "数据库无法读取" } }
            }
        }

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
    fun provideClock(): Clock = Clock.systemUTC()
}
