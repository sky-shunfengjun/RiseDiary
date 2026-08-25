package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.risediary.app.R
import com.risediary.app.ui.theme.CardBlue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Single-month paginated calendar showing flight counts with blue circles.
 * Left/right arrow buttons navigate between months. Defaults to current month.
 *
 * Display rules:
 * - Today (no flights): hollow blue circle outline
 * - Today (has flights): hollow circle + filled blue circle inside (intensity by count)
 * - Other day with flights: filled blue circle (intensity by count)
 * - Other day, no flights: date number only
 *
 * @param dayCounts Map of "yyyy-MM-dd" → flight count
 */
@Composable
fun CalendarHeatmap(
    dayCounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val currentYearMonth = remember(today) { YearMonth.from(today) }
    var displayedMonth by remember { mutableStateOf(currentYearMonth) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    val isCurrentMonth = displayedMonth == currentYearMonth

    Column(modifier = modifier) {
        // ── Header: 〈 2026年6月 〉 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "〈",
                fontSize = 22.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier
                    .clickable { displayedMonth = displayedMonth.minusMonths(1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )

            Text(
                text = stringResource(
                    R.string.calendar_month_format,
                    displayedMonth.year,
                    displayedMonth.monthValue
                ),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Text(
                text = "〉",
                fontSize = 22.sp,
                color = if (isCurrentMonth)
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                else
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier
                    .then(
                        if (!isCurrentMonth) Modifier.clickable {
                            displayedMonth = displayedMonth.plusMonths(1)
                        }
                        else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Month grid ──
        MonthGrid(displayedMonth, today, dayCounts, dateFormatter)

        Spacer(modifier = Modifier.height(8.dp))

        // ── Legend ──
        CalendarLegend()
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    today: LocalDate,
    dayCounts: Map<String, Int>,
    fmt: DateTimeFormatter
) {
    val firstDay = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val startOffset = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val totalRows = (startOffset + daysInMonth + 6) / 7

    val weekdayLabels = stringArrayResource(R.array.weekday_labels)

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        for (row in 0 until totalRows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val day = row * 7 + col - startOffset + 1

                    if (day in 1..daysInMonth) {
                        val date = firstDay.withDayOfMonth(day)
                        val dateStr = date.format(fmt)
                        val count = dayCounts[dateStr] ?: 0
                        val isToday = date == today

                        CalendarCell(
                            day = day,
                            count = count,
                            isToday = isToday,
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

/**
 * Display rules:
 * - Today (any count): always hollow blue circle border
 * - Today + flights: hollow circle + filled blue circle inside (intensity by count)
 * - Other day + flights: filled blue circle only (intensity by count)
 * - Other day, no flights: date number only
 */
@Composable
private fun CalendarCell(
    day: Int,
    count: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val hasFlights = count > 0
    val fillAlpha = when {
        count >= 4 -> 0.7f
        count == 3 -> 0.5f
        count == 2 -> 0.35f
        count == 1 -> 0.2f
        else -> 0f
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Filled circle — shown for non-today with flights, or today with flights
        if (hasFlights) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(CardBlue.copy(alpha = fillAlpha))
            )
        }

        // Hollow circle border — always shown for today
        if (isToday) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, CardBlue.copy(alpha = 0.55f), CircleShape)
            )
        }

        // Date number (on top of circles)
        Text(
            text = "$day",
            fontSize = 13.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) CardBlue
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("0", fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        Spacer(modifier = Modifier.width(6.dp))
        listOf(0.2f, 0.35f, 0.5f, 0.7f).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(CardBlue.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text("4+", fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f))
    }
}
