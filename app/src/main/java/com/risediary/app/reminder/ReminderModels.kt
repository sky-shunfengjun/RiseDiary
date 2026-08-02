package com.risediary.app.reminder

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderType(
    val storedValue: String,
    val uniqueWorkName: String,
    val notificationId: Int
) {
    DAILY("daily", "reminder_daily", 2101),
    INACTIVE("inactive", "reminder_inactive", 2102),
    MONTHLY_LENGTH("monthly_length", "reminder_monthly_length", 2103);

    companion object {
        fun fromStoredValue(value: String?): ReminderType? =
            entries.firstOrNull { it.storedValue == value }
    }
}

enum class NotificationDestination(val storedValue: String) {
    RECORDS("records"),
    LENGTH_HISTORY("length_history");

    companion object {
        const val EXTRA_DESTINATION = "notification_destination"

        fun fromStoredValue(value: String?): NotificationDestination? =
            entries.firstOrNull { it.storedValue == value }
    }
}

data class ReminderConfiguration(
    val dailyEnabled: Boolean = false,
    val dailyTime: String = DEFAULT_TIME,
    val inactiveEnabled: Boolean = false,
    val inactiveDays: Int = DEFAULT_INACTIVE_DAYS,
    val inactiveTime: String = DEFAULT_TIME,
    val monthlyLengthEnabled: Boolean = false,
    val monthlyLengthDay: Int = DEFAULT_MONTHLY_DAY,
    val monthlyLengthTime: String = DEFAULT_TIME
) {
    val hasEnabledReminders: Boolean
        get() = dailyEnabled || inactiveEnabled || monthlyLengthEnabled

    fun isEnabled(type: ReminderType): Boolean = when (type) {
        ReminderType.DAILY -> dailyEnabled
        ReminderType.INACTIVE -> inactiveEnabled
        ReminderType.MONTHLY_LENGTH -> monthlyLengthEnabled
    }

    fun time(type: ReminderType): LocalTime = parseReminderTime(
        when (type) {
            ReminderType.DAILY -> dailyTime
            ReminderType.INACTIVE -> inactiveTime
            ReminderType.MONTHLY_LENGTH -> monthlyLengthTime
        }
    )

    companion object {
        const val DEFAULT_TIME = "22:00"
        const val DEFAULT_INACTIVE_DAYS = 7
        const val DEFAULT_MONTHLY_DAY = 1
        val INACTIVE_DAY_OPTIONS = listOf(3, 7, 14, 30)
    }
}

enum class ReminderAlarmPrecision {
    EXACT,
    INEXACT
}

object ReminderAlarmPolicy {
    fun precision(sdkInt: Int, exactAccessGranted: Boolean): ReminderAlarmPrecision =
        if (sdkInt < 31 || exactAccessGranted) {
            ReminderAlarmPrecision.EXACT
        } else {
            ReminderAlarmPrecision.INEXACT
        }
}

data class ReminderRuntimeState(
    val inactiveEnabledEpochDay: Long = UNSET_EPOCH_DAY,
    val dailyLastSentEpochDay: Long = UNSET_EPOCH_DAY,
    val inactiveLastSentEpochDay: Long = UNSET_EPOCH_DAY,
    val monthlyLastSent: String = ""
) {
    companion object {
        const val UNSET_EPOCH_DAY = -1L
    }
}

internal fun parseReminderTime(value: String): LocalTime =
    runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(22, 0))

internal fun normalizeReminderTime(value: String): String =
    parseReminderTime(value).toString()

internal fun normalizeInactiveDays(value: Int): Int =
    ReminderConfiguration.INACTIVE_DAY_OPTIONS.minBy { option ->
        kotlin.math.abs(option - value)
    }

object ReminderScheduleCalculator {
    fun nextDaily(now: ZonedDateTime, time: LocalTime): ZonedDateTime =
        nextOccurrence(now, now.toLocalDate(), time)

    fun nextMonthly(
        now: ZonedDateTime,
        dayOfMonth: Int,
        time: LocalTime
    ): ZonedDateTime {
        val safeDay = dayOfMonth.coerceIn(1, 28)
        val currentMonth = YearMonth.from(now)
        val currentCandidate = atLocalTime(
            currentMonth.atDay(safeDay),
            time,
            now.zone
        )
        return if (currentCandidate.isAfter(now)) {
            currentCandidate
        } else {
            atLocalTime(currentMonth.plusMonths(1).atDay(safeDay), time, now.zone)
        }
    }

    fun nextInactive(
        now: ZonedDateTime,
        anchorDate: LocalDate,
        lastSentDate: LocalDate?,
        intervalDays: Int,
        time: LocalTime
    ): ZonedDateTime {
        val safeInterval = intervalDays.coerceAtLeast(1)
        val baseDate = lastSentDate
            ?.takeIf { !it.isBefore(anchorDate) }
            ?: anchorDate
        val due = atLocalTime(baseDate.plusDays(safeInterval.toLong()), time, now.zone)
        return if (due.isAfter(now)) due else nextOccurrence(now, now.toLocalDate(), time)
    }

    fun isInactiveDue(
        today: LocalDate,
        anchorDate: LocalDate,
        lastSentDate: LocalDate?,
        intervalDays: Int
    ): Boolean {
        val safeInterval = intervalDays.coerceAtLeast(1)
        if (today.isBefore(anchorDate.plusDays(safeInterval.toLong()))) return false
        val validLastSent = lastSentDate?.takeIf { !it.isBefore(anchorDate) } ?: return true
        return !today.isBefore(validLastSent.plusDays(safeInterval.toLong()))
    }

    private fun nextOccurrence(
        now: ZonedDateTime,
        date: LocalDate,
        time: LocalTime
    ): ZonedDateTime {
        val todayCandidate = atLocalTime(date, time, now.zone)
        return if (todayCandidate.isAfter(now)) {
            todayCandidate
        } else {
            atLocalTime(date.plusDays(1), time, now.zone)
        }
    }

    private fun atLocalTime(
        date: LocalDate,
        time: LocalTime,
        zoneId: ZoneId
    ): ZonedDateTime = date.atTime(time).atZone(zoneId)
}

object ReminderFallbackPolicy {
    const val DELAY_MINUTES = 15L

    fun target(primaryTarget: ZonedDateTime): ZonedDateTime =
        primaryTarget.plusMinutes(DELAY_MINUTES)
}

object ReminderDeliveryPolicy {
    fun shouldDeliverDaily(
        hasRecordToday: Boolean,
        lastSentEpochDay: Long,
        todayEpochDay: Long
    ): Boolean = !hasRecordToday && lastSentEpochDay != todayEpochDay

    fun shouldDeliverMonthly(
        hasRecordThisMonth: Boolean,
        lastSentYearMonth: String,
        currentYearMonth: String
    ): Boolean = !hasRecordThisMonth && lastSentYearMonth != currentYearMonth
}
