package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LiquidSingleDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var visibleMonth by remember(initialDate) {
        mutableStateOf(YearMonth.from(initialDate))
    }

    LiquidDatePickerDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(text = "取消", onClick = onDismissRequest)
        },
        confirmButton = {
            TextButton(
                text = "确定",
                onClick = { onConfirm(selectedDate) },
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    ) {
        CompactCalendar(
            title = "选择日期",
            visibleMonth = visibleMonth,
            onVisibleMonthChange = { visibleMonth = it },
            selectedStart = selectedDate,
            selectedEnd = null,
            onDateSelected = { selectedDate = it }
        )
    }
}

@Composable
fun LiquidDateRangePickerDialog(
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var selectedStart by remember { mutableStateOf<LocalDate?>(null) }
    var selectedEnd by remember { mutableStateOf<LocalDate?>(null) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }

    LiquidDatePickerDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(text = "取消", onClick = onDismissRequest)
        },
        confirmButton = {
            TextButton(
                text = "确定",
                enabled = selectedStart != null,
                onClick = {
                    val start = selectedStart
                    if (start != null) {
                        onConfirm(start, selectedEnd ?: start)
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    ) {
        CompactCalendar(
            title = when {
                selectedStart == null -> "选择开始日期"
                selectedEnd == null -> "选择结束日期"
                else -> "已选择日期范围"
            },
            visibleMonth = visibleMonth,
            onVisibleMonthChange = { visibleMonth = it },
            selectedStart = selectedStart,
            selectedEnd = selectedEnd,
            onDateSelected = { date ->
                val start = selectedStart
                val end = selectedEnd
                when {
                    start == null || end != null -> {
                        selectedStart = date
                        selectedEnd = null
                    }
                    date.isBefore(start) -> {
                        selectedStart = date
                        selectedEnd = null
                    }
                    else -> selectedEnd = date
                }
            }
        )
    }
}

@Composable
private fun CompactCalendar(
    title: String,
    visibleMonth: YearMonth,
    onVisibleMonthChange: (YearMonth) -> Unit,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
    val firstOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val dayCells = remember(visibleMonth) {
        List(42) { cellIndex ->
            val day = cellIndex - firstOffset + 1
            if (day in 1..visibleMonth.lengthOfMonth()) visibleMonth.atDay(day) else null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${visibleMonth.year}年${visibleMonth.monthValue}月",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            CalendarNavButton(
                contentDescription = "上个月",
                onClick = { onVisibleMonthChange(visibleMonth.minusMonths(1)) }
            ) {
                Icon(Icons.Default.ChevronLeft, "上个月")
            }
            Spacer(Modifier.width(6.dp))
            CalendarNavButton(
                contentDescription = "下个月",
                onClick = { onVisibleMonthChange(visibleMonth.plusMonths(1)) }
            ) {
                Icon(Icons.Default.ChevronRight, "下个月")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { weekday ->
                Text(
                    text = weekday,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            dayCells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                CalendarDay(
                                    date = date,
                                    selectedStart = selectedStart,
                                    selectedEnd = selectedEnd,
                                    onClick = { onDateSelected(date) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onClick: () -> Unit
) {
    val isSelected = date == selectedStart || date == selectedEnd
    val isInRange =
        selectedStart != null &&
            selectedEnd != null &&
            date.isAfter(selectedStart) &&
            date.isBefore(selectedEnd)
    val isToday = date == LocalDate.now()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = if (isSelected) CircleShape else RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier.background(MiuixTheme.colorScheme.primary)
                    isInRange -> Modifier.background(
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)
                    )
                    isToday -> Modifier.border(
                        1.dp,
                        MiuixTheme.colorScheme.primary,
                        CircleShape
                    )
                    else -> Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CalendarNavButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.055f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
