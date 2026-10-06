package com.risediary.app.reminder

import com.risediary.app.data.UserPreferences
import com.risediary.app.data.repository.FlightRepository
import com.risediary.app.data.repository.LengthRecordRepository
import com.risediary.app.util.LocalTimeRanges
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ReminderDeliveryCoordinator internal constructor(
    private val preferences: UserPreferences,
    private val flightRepository: FlightRepository,
    private val lengthRepository: LengthRecordRepository,
    private val clock: Clock,
    private val plans: ReminderPlanRepository? = null,
    private val postReminder: (ReminderType) -> Boolean
) {
    @Inject internal constructor(preferences: UserPreferences, flightRepository: FlightRepository,
        lengthRepository: LengthRecordRepository, notifier: ReminderNotifier, clock: Clock,
        plans: ReminderPlanStore) : this(preferences, flightRepository, lengthRepository, clock, plans,
        notifier::post)
    private val deliveryMutex = Mutex()

    internal suspend fun deliver(type: ReminderType, expected: ReminderPlan? = null) {
        // Reads, notification, marker and plan consumption share the maintenance permit.
        preferences.maintenanceGate.write {
            deliveryMutex.withLock {
                val configuration = preferences.getReminderConfiguration()
                if (!configuration.isEnabled(type)) return@withLock
                val zone = ZoneId.systemDefault()
                val now = Instant.now(clock).atZone(zone)
                val current = plans?.load(type)
                val currentMatches = current?.matches(type, configuration, zone) == true
                val plan = if (expected != null) {
                    if (!currentMatches || current.consumed ||
                        expected.consumed || !current.sameOccurrence(expected)) return@withLock
                    if (current.targetMillis > now.toInstant().toEpochMilli()) return@withLock
                    current
                } else {
                    // Old installed jobs had no payload. A matching due ledger still gives
                    // them the original target; otherwise infer the last configured occurrence.
                    if (currentMatches && current.consumed) return@withLock
                    current?.takeIf { currentMatches && !it.consumed &&
                        it.targetMillis <= now.toInstant().toEpochMilli() }
                }
                val target = plan?.let { Instant.ofEpochMilli(it.targetMillis).atZone(ZoneId.of(it.zoneId)) }
                    ?: ReminderScheduleCalculator.previousOccurrence(now, type, configuration)
                when (type) {
                    ReminderType.DAILY -> deliverDaily(target.toLocalDate(), target.zone)
                    ReminderType.INACTIVE -> deliverInactive(configuration, target.toLocalDate(), target.zone)
                    ReminderType.MONTHLY_LENGTH -> deliverMonthly(YearMonth.from(target), target.zone)
                }
                // Skipped records, already-delivered targets and denied notification access
                // all complete this occurrence. Only an actual successful post writes sent.
                plan?.let { plans?.save(it.copy(consumed = true)) }
            }
        }
    }

    private suspend fun deliverDaily(day: LocalDate, zone: ZoneId) {
        val (dayStart, dayEnd) = LocalTimeRanges.day(day, zone)
        val runtime = preferences.getReminderRuntimeState()
        if (ReminderDeliveryPolicy.shouldDeliverDaily(
                hasRecordToday = flightRepository.countByRange(dayStart, dayEnd) > 0,
                lastSentEpochDay = runtime.dailyLastSentEpochDay,
                todayEpochDay = day.toEpochDay()) && postReminder(ReminderType.DAILY)) {
            preferences.markDailyReminderSent(day.toEpochDay())
        }
    }

    private suspend fun deliverInactive(configuration: ReminderConfiguration, day: LocalDate, zone: ZoneId) {
        val runtime = preferences.getReminderRuntimeState()
        val latestRecordDate = flightRepository.getRecent(1).firstOrNull()?.let {
            Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
        }
        val anchorDate = latestRecordDate ?: runtime.inactiveEnabledEpochDay.takeIf { it >= 0L }
            ?.let(LocalDate::ofEpochDay) ?: day.also {
                preferences.ensureInactiveReminderAnchor(it.toEpochDay())
            }
        val lastSentDate = runtime.inactiveLastSentEpochDay.takeIf { it >= 0L }?.let(LocalDate::ofEpochDay)
        if (ReminderScheduleCalculator.isInactiveDue(day, anchorDate, lastSentDate,
                configuration.inactiveDays) && postReminder(ReminderType.INACTIVE)) {
            preferences.markInactiveReminderSent(day.toEpochDay())
        }
    }

    private suspend fun deliverMonthly(month: YearMonth, zone: ZoneId) {
        val runtime = preferences.getReminderRuntimeState()
        val hasMonthRecord = lengthRepository.getAll().any {
            YearMonth.from(Instant.ofEpochMilli(it.recordDate).atZone(zone)) == month
        }
        if (ReminderDeliveryPolicy.shouldDeliverMonthly(hasMonthRecord, runtime.monthlyLastSent,
                month.toString()) && postReminder(ReminderType.MONTHLY_LENGTH)) {
            preferences.markMonthlyReminderSent(month.toString())
        }
    }
}
