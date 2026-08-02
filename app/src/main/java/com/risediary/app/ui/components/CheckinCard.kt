package com.risediary.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.risediary.app.R
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.StatusSuccess
import com.risediary.app.ui.theme.StatusWarning
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Combined check-in card: Duolingo-style week view (default) + month calendar (toggle).
 *
 * @param dayCounts   Map of "yyyy-MM-dd" → flight count (for both week circles and month heatmap)
 * @param weekCount   Total flights this week
 * @param lastWeekCount Flights last week (for comparison arrow)
 */
@Composable
fun CheckinCard(
    dayCounts: Map<String, Int>,
    weekCount: Int,
    lastWeekCount: Int,
    modifier: Modifier = Modifier
) {
    var showMonth by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    // Calculate this week's Monday–Sunday
    val weekStart = remember(today) {
        val dayOfWeek = today.dayOfWeek.value  // 1=Mon ... 7=Sun
        today.minusDays((dayOfWeek - 1).toLong())
    }
    val weekDays = remember(weekStart) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(modifier = modifier.animateContentSize()) {
        // Header row with toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "打卡",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
            val currentTrackColor by rememberUpdatedState(trackColor)
            val backdrop = rememberLayerBackdrop { drawContent() }
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(38.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .requiredHeight(70.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(124.dp)
                                .height(38.dp)
                                .clip(ContinuousCapsule)
                                .background(currentTrackColor)
                        )
                    }
                    LiquidSegmentedControl(
                        options = remember {
                            listOf(
                                LiquidSegmentOption("本周", Icons.Default.CalendarMonth),
                                LiquidSegmentOption("本月", Icons.Default.CalendarMonth)
                            )
                        },
                        selectedIndex = if (showMonth) 1 else 0,
                        onSelected = { showMonth = it == 1 },
                        backdrop = backdrop,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(124.dp),
                        containerHeight = 38.dp,
                        contentPadding = 3.dp,
                        showIcons = false,
                        labelFontSize = 12.sp,
                        showSelectionShadow = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = showMonth,
            transitionSpec = {
                (
                    fadeIn(tween(180)) +
                        slideInHorizontally(tween(220)) { width ->
                            if (targetState) width / 12 else -width / 12
                        }
                    ).togetherWith(
                    fadeOut(tween(120)) +
                        slideOutHorizontally(tween(180)) { width ->
                            if (targetState) -width / 12 else width / 12
                        }
                )
            },
            label = "checkin_range_content"
        ) { showingMonth ->
            if (showingMonth) {
                CalendarHeatmap(
                    dayCounts = dayCounts,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // ── Duolingo-style week check-in ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        weekDays.forEachIndexed { index, day ->
                            val dateStr = day.format(dateFormatter)
                            val count = dayCounts[dateStr] ?: 0
                            val isToday = day == today
                            val isFlown = count > 0

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(40.dp)
                            ) {
                                Text(
                                    weekdayLabels[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .then(
                                            if (isFlown) {
                                                Modifier
                                                    .clip(CircleShape)
                                                    .background(CardBlue)
                                            } else if (isToday) {
                                                Modifier
                                                    .clip(CircleShape)
                                                    .border(2.dp, CardBlue, CircleShape)
                                            } else {
                                                Modifier
                                                    .clip(CircleShape)
                                                    .border(
                                                        1.5.dp,
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                        CircleShape
                                                    )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFlown) {
                                        Canvas(Modifier.size(18.dp)) {
                                            val w = size.width
                                            val h = size.height
                                            val checkColor = Color.White
                                            drawLine(
                                                checkColor,
                                                Offset(w * 0.2f, h * 0.5f),
                                                Offset(w * 0.45f, h * 0.75f),
                                                strokeWidth = 2.5f
                                            )
                                            drawLine(
                                                checkColor,
                                                Offset(w * 0.45f, h * 0.75f),
                                                Offset(w * 0.8f, h * 0.25f),
                                                strokeWidth = 2.5f
                                            )
                                        }
                                    } else {
                                        Text(
                                            "${day.dayOfMonth}",
                                            fontSize = 14.sp,
                                            fontWeight =
                                                if (isToday) FontWeight.Bold else FontWeight.Normal,
                                            color =
                                                if (isToday) CardBlue
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val summaryText = if (weekCount == 0) {
                        androidx.compose.ui.res.stringResource(R.string.home_week_summary_empty)
                    } else {
                        androidx.compose.ui.res.stringResource(
                            R.string.home_week_summary_recorded,
                            weekCount
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        if (lastWeekCount > 0) {
                            val change = (
                                (weekCount - lastWeekCount).toFloat() /
                                    lastWeekCount * 100
                                ).toInt()
                            val arrow = if (change >= 0) "↑" else "↓"
                            val changeColor = if (change >= 0) StatusSuccess else StatusWarning
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "较上周 $arrow ${kotlin.math.abs(change)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = changeColor
                            )
                        }
                    }
                }
            }
        }
    }
}
