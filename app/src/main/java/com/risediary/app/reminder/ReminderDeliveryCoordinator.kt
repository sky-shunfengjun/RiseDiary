package com.risediary.app.reminder

import com.risediary.app.data.UserPreferences
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.util.LocalTimeRanges
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderDeliveryCoordinator @Inject constructor(
    private val preferences: UserPreferences,
    private val flightRepository: FlightRepository,
    private val lengthRepository: LengthRecordRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock
) {
    private val deliveryMutex = Mutex()

    suspend fun deliver(type: ReminderType) {
        deliveryMutex.withLock {
            val configuration = preferences.reminderConfiguration.first()
            if (!configuration.isEnabled(type)) return@withLock
            when (type) {
                ReminderType.DAILY -> deliverDaily()
                ReminderType.INACTIVE -> deliverInactive(configuration)
                ReminderType.MONTHLY_LENGTH -> deliverMonthly()
            }
        }
    }

    private suspend fun deliverDaily() {
        val zoneId = ZoneId.systemDefault()
        val today = Instant.now(clock).atZone(zoneId).toLocalDate()
        val (dayStart, dayEnd) = LocalTimeRanges.day(today, zoneId)
        val runtime = preferences.getReminderRuntimeState()
        if (
            ReminderDeliveryPolicy.shouldDeliverDaily(
                hasRecordToday = flightRepository.countByRange(dayStart, dayEnd) > 0,
                lastSentEpochDay = runtime.dailyLastSentEpochDay,
                todayEpochDay = today.toEpochDay()
            ) &&
            notifier.post(ReminderType.DAILY)
        ) {
            preferences.markDailyReminderSent(today.toEpochDay())
        }
    }

    private suspend fun deliverInactive(configuration: ReminderConfiguration) {
        val zoneId = ZoneId.systemDefault()
        val today = Instant.now(clock).atZone(zoneId).toLocalDate()
        val runtime = preferences.getReminderRuntimeState()
        val latestRecordDate = flightRepository.getRecent(1)
            .firstOrNull()
            ?.let { flight ->
                Instant.ofEpochMilli(flight.startTime).atZone(zoneId).toLocalDate()
            }
        val anchorDate = latestRecordDate ?: runtime.inactiveEnabledEpochDay
            .takeIf { it >= 0L }
            ?.let(LocalDate::ofEpochDay)
            ?: today.also { preferences.ensureInactiveReminderAnchor(it.toEpochDay()) }
        val lastSentDate = runtime.inactiveLastSentEpochDay
            .takeIf { it >= 0L }
            ?.let(LocalDate::ofEpochDay)
        if (
            ReminderScheduleCalculator.isInactiveDue(
                today = today,
                anchorDate = anchorDate,
                lastSentDate = lastSentDate,
                intervalDays = configuration.inactiveDays
            ) &&
            notifier.post(ReminderType.INACTIVE)
        ) {
            preferences.markInactiveReminderSent(today.toEpochDay())
        }
    }

    private suspend fun deliverMonthly() {
        val zoneId = ZoneId.systemDefault()
        val currentMonth = YearMonth.from(Instant.now(clock).atZone(zoneId))
        val runtime = preferences.getReminderRuntimeState()
        val hasCurrentMonthRecord = lengthRepository.getAll().any { record ->
            YearMonth.from(
                Instant.ofEpochMilli(record.recordDate).atZone(zoneId)
            ) == currentMonth
        }
        if (
            ReminderDeliveryPolicy.shouldDeliverMonthly(
                hasRecordThisMonth = hasCurrentMonthRecord,
                lastSentYearMonth = runtime.monthlyLastSent,
                currentYearMonth = currentMonth.toString()
            ) &&
            notifier.post(ReminderType.MONTHLY_LENGTH)
        ) {
            preferences.markMonthlyReminderSent(currentMonth.toString())
        }
    }
}
