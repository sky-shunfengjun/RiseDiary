package com.risediary.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object LocalTimeRanges {
    fun day(date: LocalDate, zoneId: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun weekContaining(date: LocalDate, zoneId: ZoneId): Pair<Long, Long> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zoneId).toInstant().toEpochMilli() to
            monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun monthContaining(date: LocalDate, zoneId: ZoneId): Pair<Long, Long> {
        val start = date.withDayOfMonth(1)
        return start.atStartOfDay(zoneId).toInstant().toEpochMilli() to
            start.plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun yearContaining(date: LocalDate, zoneId: ZoneId): Pair<Long, Long> {
        val start = date.withDayOfYear(1)
        return start.atStartOfDay(zoneId).toInstant().toEpochMilli() to
            start.plusYears(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun localDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
}
