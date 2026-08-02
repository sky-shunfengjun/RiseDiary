package com.risediary.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.repository.FlightRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferences,
    private val flightRepository: FlightRepository,
    private val clock: Clock
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(context)
    }

    suspend fun observeConfiguration() {
        preferences.reminderConfiguration
            .distinctUntilChanged()
            .collectLatest { configuration -> syncAll(configuration) }
    }

    suspend fun syncAll() {
        syncAll(preferences.reminderConfiguration.first())
    }

    private suspend fun syncAll(configuration: ReminderConfiguration) {
        ReminderType.entries.forEach { type -> sync(type, configuration) }
    }

    suspend fun sync(type: ReminderType) {
        sync(type, preferences.reminderConfiguration.first())
    }

    suspend fun onReminderEnabledChanged(type: ReminderType, enabled: Boolean) {
        if (enabled) {
            sync(type)
            return
        }
        cancel(type)
        if (!preferences.reminderConfiguration.first().hasEnabledReminders) {
            cancelBackgroundTest()
        }
    }

    suspend fun rescheduleAfterFallback(type: ReminderType) {
        sync(
            type = type,
            configuration = preferences.reminderConfiguration.first(),
            existingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE
        )
    }

    private suspend fun sync(
        type: ReminderType,
        configuration: ReminderConfiguration,
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        if (!configuration.isEnabled(type)) {
            cancel(type)
            return
        }

        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)
        val target = when (type) {
            ReminderType.DAILY ->
                ReminderScheduleCalculator.nextDaily(now, configuration.time(type))

            ReminderType.MONTHLY_LENGTH ->
                ReminderScheduleCalculator.nextMonthly(
                    now,
                    configuration.monthlyLengthDay,
                    configuration.time(type)
                )

            ReminderType.INACTIVE -> {
                val runtime = preferences.getReminderRuntimeState()
                val latestRecordDate = flightRepository.getRecent(1)
                    .firstOrNull()
                    ?.let { flight ->
                        Instant.ofEpochMilli(flight.startTime).atZone(zoneId).toLocalDate()
                    }
                val anchorDate = latestRecordDate ?: runtime.inactiveEnabledEpochDay
                    .takeIf { it >= 0L }
                    ?.let(LocalDate::ofEpochDay)
                    ?: now.toLocalDate().also { date ->
                        preferences.ensureInactiveReminderAnchor(date.toEpochDay())
                    }
                val lastSentDate = runtime.inactiveLastSentEpochDay
                    .takeIf { it >= 0L }
                    ?.let(LocalDate::ofEpochDay)
                ReminderScheduleCalculator.nextInactive(
                    now = now,
                    anchorDate = anchorDate,
                    lastSentDate = lastSentDate,
                    intervalDays = configuration.inactiveDays,
                    time = configuration.time(type)
                )
            }
        }

        val fallbackTarget = ReminderFallbackPolicy.target(target)
        val fallbackDelayMillis =
            Duration.between(now, fallbackTarget).toMillis().coerceAtLeast(1L)
        scheduleAlarm(type, target.toInstant().toEpochMilli())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ReminderWorker.KEY_REMINDER_TYPE, type.storedValue)
                    .build()
            )
            .setInitialDelay(fallbackDelayMillis, TimeUnit.MILLISECONDS)
            .addTag(type.uniqueWorkName)
            .build()
        workManager.enqueueUniqueWork(
            type.uniqueWorkName,
            existingWorkPolicy,
            request
        )
    }

    fun cancel(type: ReminderType) {
        existingAlarmPendingIntent(type)?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        workManager.cancelUniqueWork(type.uniqueWorkName)
        NotificationManagerCompat.from(context).cancel(type.notificationId)
    }

    fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

    fun scheduleBackgroundTest(): Boolean {
        if (!exactAlarmsAllowed()) return false
        val pendingIntent = backgroundTestPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return false
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + BACKGROUND_TEST_DELAY_MILLIS,
                pendingIntent
            )
            true
        } catch (_: SecurityException) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            false
        }
    }

    fun cancelBackgroundTest() {
        backgroundTestPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun scheduleAlarm(type: ReminderType, triggerAtMillis: Long) {
        val pendingIntent = alarmPendingIntent(type, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        val precision = ReminderAlarmPolicy.precision(
            sdkInt = Build.VERSION.SDK_INT,
            exactAccessGranted = exactAlarmsAllowed()
        )
        if (precision == ReminderAlarmPrecision.EXACT) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            } catch (_: SecurityException) {
                // Permission can be revoked between the capability check and scheduling.
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun existingAlarmPendingIntent(type: ReminderType): PendingIntent? =
        alarmPendingIntent(type, PendingIntent.FLAG_NO_CREATE)

    private fun alarmPendingIntent(type: ReminderType, creationFlag: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = "${context.packageName}.REMINDER_ALARM.${type.storedValue}"
            putExtra(ReminderWorker.KEY_REMINDER_TYPE, type.storedValue)
        }
        return PendingIntent.getBroadcast(
            context,
            type.notificationId,
            intent,
            creationFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun backgroundTestPendingIntent(creationFlag: Int): PendingIntent? {
        val intent = Intent(context, ReminderTestAlarmReceiver::class.java).apply {
            action = "${context.packageName}.REMINDER_ALARM.TEST"
        }
        return PendingIntent.getBroadcast(
            context,
            BACKGROUND_TEST_REQUEST_CODE,
            intent,
            creationFlag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun onFlightDataChanged() {
        cancel(ReminderType.DAILY)
        cancel(ReminderType.INACTIVE)
        resetInactiveAnchorForEmptyHistory()
        sync(ReminderType.DAILY)
        sync(ReminderType.INACTIVE)
    }

    suspend fun onLengthDataChanged() {
        cancel(ReminderType.MONTHLY_LENGTH)
        sync(ReminderType.MONTHLY_LENGTH)
    }

    suspend fun onAllDataChanged() {
        ReminderType.entries.forEach(::cancel)
        cancelBackgroundTest()
        resetInactiveAnchorForEmptyHistory()
        syncAll()
    }

    private suspend fun resetInactiveAnchorForEmptyHistory() {
        val configuration = preferences.reminderConfiguration.first()
        if (configuration.inactiveEnabled && flightRepository.getRecent(1).isEmpty()) {
            val today = ZonedDateTime.now(clock)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDate()
            preferences.resetInactiveReminderAnchor(today.toEpochDay())
        }
    }

    private companion object {
        const val BACKGROUND_TEST_DELAY_MILLIS = 60_000L
        const val BACKGROUND_TEST_REQUEST_CODE = 2105
    }
}
