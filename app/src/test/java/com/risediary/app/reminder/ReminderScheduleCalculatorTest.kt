package com.risediary.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun dailyBeforeConfiguredTime_schedulesToday() {
        val now = zoned(2026, 7, 29, 21, 0)

        val result = ReminderScheduleCalculator.nextDaily(now, LocalTime.of(22, 0))

        assertEquals(zoned(2026, 7, 29, 22, 0), result)
    }

    @Test
    fun dailyAfterConfiguredTime_schedulesTomorrow() {
        val now = zoned(2026, 7, 29, 22, 1)

        val result = ReminderScheduleCalculator.nextDaily(now, LocalTime.of(22, 0))

        assertEquals(zoned(2026, 7, 30, 22, 0), result)
    }

    @Test
    fun fallbackRunsFifteenMinutesAfterPrimaryTarget() {
        val primary = zoned(2026, 7, 29, 23, 55)

        val result = ReminderFallbackPolicy.target(primary)

        assertEquals(zoned(2026, 7, 30, 0, 10), result)
    }

    @Test
    fun exactAlarmPolicyRequiresSpecialAccessFromAndroid12() {
        assertEquals(
            ReminderAlarmPrecision.EXACT,
            ReminderAlarmPolicy.precision(sdkInt = 30, exactAccessGranted = false)
        )
        assertEquals(
            ReminderAlarmPrecision.INEXACT,
            ReminderAlarmPolicy.precision(sdkInt = 31, exactAccessGranted = false)
        )
        assertEquals(
            ReminderAlarmPrecision.EXACT,
            ReminderAlarmPolicy.precision(sdkInt = 31, exactAccessGranted = true)
        )
    }

    @Test
    fun configurationKnowsWhenAllReminderResourcesCanBeCleared() {
        assertFalse(ReminderConfiguration().hasEnabledReminders)
        assertTrue(ReminderConfiguration(dailyEnabled = true).hasEnabledReminders)
        assertTrue(ReminderConfiguration(inactiveEnabled = true).hasEnabledReminders)
        assertTrue(ReminderConfiguration(monthlyLengthEnabled = true).hasEnabledReminders)
    }

    @Test
    fun monthlyCrossesYearBoundary() {
        val now = zoned(2026, 12, 28, 23, 0)

        val result = ReminderScheduleCalculator.nextMonthly(
            now,
            dayOfMonth = 28,
            time = LocalTime.of(22, 0)
        )

        assertEquals(zoned(2027, 1, 28, 22, 0), result)
    }

    @Test
    fun monthlyDayIsClampedToSafeRange() {
        val now = zoned(2026, 2, 1, 8, 0)

        val result = ReminderScheduleCalculator.nextMonthly(
            now,
            dayOfMonth = 31,
            time = LocalTime.of(22, 0)
        )

        assertEquals(zoned(2026, 2, 28, 22, 0), result)
    }

    @Test
    fun inactiveUsesEnableDateWhenNoRecordExists() {
        val now = zoned(2026, 7, 29, 12, 0)

        val result = ReminderScheduleCalculator.nextInactive(
            now = now,
            anchorDate = LocalDate.of(2026, 7, 29),
            lastSentDate = null,
            intervalDays = 7,
            time = LocalTime.of(22, 0)
        )

        assertEquals(zoned(2026, 8, 5, 22, 0), result)
    }

    @Test
    fun inactiveOverdue_waitsForNextConfiguredTime() {
        val now = zoned(2026, 7, 29, 21, 0)

        val result = ReminderScheduleCalculator.nextInactive(
            now = now,
            anchorDate = LocalDate.of(2026, 7, 1),
            lastSentDate = null,
            intervalDays = 7,
            time = LocalTime.of(22, 0)
        )

        assertEquals(zoned(2026, 7, 29, 22, 0), result)
    }

    @Test
    fun inactiveRepeatUsesLastSentDate() {
        val today = LocalDate.of(2026, 7, 29)
        val anchor = LocalDate.of(2026, 7, 1)

        assertFalse(
            ReminderScheduleCalculator.isInactiveDue(
                today = today,
                anchorDate = anchor,
                lastSentDate = LocalDate.of(2026, 7, 23),
                intervalDays = 7
            )
        )
        assertTrue(
            ReminderScheduleCalculator.isInactiveDue(
                today = LocalDate.of(2026, 7, 30),
                anchorDate = anchor,
                lastSentDate = LocalDate.of(2026, 7, 23),
                intervalDays = 7
            )
        )
    }

    @Test
    fun newRecordAfterLastReminder_resetsInactiveInterval() {
        val today = LocalDate.of(2026, 7, 29)

        val due = ReminderScheduleCalculator.isInactiveDue(
            today = today,
            anchorDate = LocalDate.of(2026, 7, 25),
            lastSentDate = LocalDate.of(2026, 7, 20),
            intervalDays = 7
        )

        assertFalse(due)
    }

    @Test
    fun inactiveDayNormalizationUsesSupportedOptions() {
        assertEquals(3, normalizeInactiveDays(1))
        assertEquals(7, normalizeInactiveDays(9))
        assertEquals(14, normalizeInactiveDays(12))
        assertEquals(30, normalizeInactiveDays(365))
    }

    @Test
    fun dailyDeliverySkipsCompletedAndDuplicateDays() {
        val today = LocalDate.of(2026, 7, 29).toEpochDay()

        assertFalse(
            ReminderDeliveryPolicy.shouldDeliverDaily(
                hasRecordToday = true,
                lastSentEpochDay = -1L,
                todayEpochDay = today
            )
        )
        assertFalse(
            ReminderDeliveryPolicy.shouldDeliverDaily(
                hasRecordToday = false,
                lastSentEpochDay = today,
                todayEpochDay = today
            )
        )
        assertTrue(
            ReminderDeliveryPolicy.shouldDeliverDaily(
                hasRecordToday = false,
                lastSentEpochDay = today - 1,
                todayEpochDay = today
            )
        )
    }

    @Test
    fun monthlyDeliverySkipsCompletedAndDuplicateMonths() {
        assertFalse(
            ReminderDeliveryPolicy.shouldDeliverMonthly(
                hasRecordThisMonth = true,
                lastSentYearMonth = "",
                currentYearMonth = "2026-07"
            )
        )
        assertFalse(
            ReminderDeliveryPolicy.shouldDeliverMonthly(
                hasRecordThisMonth = false,
                lastSentYearMonth = "2026-07",
                currentYearMonth = "2026-07"
            )
        )
        assertTrue(
            ReminderDeliveryPolicy.shouldDeliverMonthly(
                hasRecordThisMonth = false,
                lastSentYearMonth = "2026-06",
                currentYearMonth = "2026-07"
            )
        )
    }

    @Test
    fun notificationDestinationRejectsUnknownValues() {
        assertEquals(
            NotificationDestination.RECORDS,
            NotificationDestination.fromStoredValue("records")
        )
        assertEquals(
            NotificationDestination.LENGTH_HISTORY,
            NotificationDestination.fromStoredValue("length_history")
        )
        assertEquals(null, NotificationDestination.fromStoredValue("unknown"))
    }

    @Test
    fun daylightSavingGap_resolvesToValidLocalTime() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, newYork)

        val result = ReminderScheduleCalculator.nextDaily(now, LocalTime.of(2, 30))

        assertEquals(LocalDate.of(2026, 3, 8), result.toLocalDate())
        assertEquals(LocalTime.of(3, 30), result.toLocalTime())
    }

    private fun zoned(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}
