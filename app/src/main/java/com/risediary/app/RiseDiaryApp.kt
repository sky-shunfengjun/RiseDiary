package com.risediary.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.risediary.app.data.SeedData
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.dao.TagDao
import com.risediary.app.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RiseDiaryApp : Application(), Configuration.Provider {

    @Inject
    lateinit var tagDao: TagDao

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        seedDefaultData()
        observeReminderConfiguration()
    }

    private fun seedDefaultData() {
        appScope.launch {
            runCatching { SeedData.initializeIfNeeded(tagDao, userPreferences) }
        }
    }

    private fun observeReminderConfiguration() {
        appScope.launch {
            reminderScheduler.observeConfiguration()
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_TIMER,
                    getString(R.string.notification_channel_timer),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_timer_description)
                },
                NotificationChannel(
                    CHANNEL_REMINDER,
                    getString(R.string.notification_channel_reminder),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notification_channel_reminder_description)
                }
            )
        )
    }

    companion object {
        const val CHANNEL_TIMER = "timer_channel"
        const val CHANNEL_REMINDER = "reminder_channel"
    }
}
