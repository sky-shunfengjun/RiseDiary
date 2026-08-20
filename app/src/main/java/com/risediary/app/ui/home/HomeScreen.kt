package com.risediary.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.components.CalendarHeatmap
import com.risediary.app.ui.components.CheckinCard
import com.risediary.app.ui.components.ACHIEVEMENT_ICONS
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.ui.components.LengthTrendChart
import com.risediary.app.ui.components.TrendColumnChart
import com.risediary.app.ui.theme.*
import com.risediary.app.util.formatNaturalDuration
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()

    // Refresh on first composition
    LaunchedEffect(Unit) { vm.refresh() }

    val username by vm.username.collectAsStateWithLifecycle()
    val todayCount by vm.todayCount.collectAsStateWithLifecycle()
    val weekCount by vm.weekCount.collectAsStateWithLifecycle()
    val monthCount by vm.monthCount.collectAsStateWithLifecycle()
    val totalCount by vm.totalCount.collectAsStateWithLifecycle()
    val avgDuration by vm.avgDuration.collectAsStateWithLifecycle()
    val maxDistance by vm.maxDistance.collectAsStateWithLifecycle()
    val weekVol by vm.weekVolumeSum.collectAsStateWithLifecycle()
    val lastWeek by vm.lastWeekCount.collectAsStateWithLifecycle()
    val dailyTipResId by vm.dailyTipResId.collectAsStateWithLifecycle()
    val lastFlightDaysAgo by vm.lastFlightDaysAgo.collectAsStateWithLifecycle()
    val averageIntervalDays by vm.averageIntervalDays.collectAsStateWithLifecycle()

    // Card ordering
    val cardOrderJson by vm.homeCardOrder.collectAsStateWithLifecycle()
    val cardVisibilityJson by vm.homeCardVisibility.collectAsStateWithLifecycle()
    val orderedCards = remember(cardOrderJson, cardVisibilityJson) {
        parseCardOrder(cardOrderJson, cardVisibilityJson)
    }
    var revealLowerCards by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealLowerCards = true }

    val todayStatus = remember(todayCount) { vm.getTodayStatus() }
    val todayStatusText = if (todayStatus.hasRecords) {
        stringResource(R.string.home_today_recorded, todayStatus.count)
    } else {
        stringResource(R.string.home_today_ready)
    }
    val dateStr = SimpleDateFormat("yyyy年M月d日  EEEE", Locale.CHINESE).format(Date())

    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            vm.refresh()
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${vm.getGreeting()}，$username",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val statusCardBg = if (todayStatus.hasRecords) CardGreen else CardBlue
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (LocalRiseDarkTheme.current) {
                            statusCardBg.copy(alpha = 0.18f)
                        } else {
                            statusCardBg.copy(alpha = 0.12f)
                        }
                    )
                    .padding(20.dp)
            ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(statusCardBg.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (todayStatus.hasRecords) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Info
                                },
                                contentDescription = null,
                                modifier = Modifier.size(25.dp),
                                tint = statusCardBg
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    if (todayStatus.hasRecords) {
                                        stringResource(R.string.home_status_today)
                                    } else {
                                        stringResource(R.string.home_status_ready)
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                todayStatusText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    val daysAgo = lastFlightDaysAgo
                    if (!todayStatus.hasRecords && daysAgo != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            when (daysAgo) {
                                0 -> stringResource(R.string.home_last_record_today)
                                1 -> stringResource(R.string.home_last_record_yesterday)
                                else -> stringResource(
                                    R.string.home_last_record_days,
                                    daysAgo
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(17.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.home_tip_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(dailyTipResId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3
                            )
                        }
                    }
            }

            Text(
                stringResource(R.string.home_overview_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )

            // === LOWER SECTION CARDS (dynamic order) ===

            val heatmapData by vm.flightCountsByDay.collectAsStateWithLifecycle()

            orderedCards.forEachIndexed { index, cardId ->
                AnimatedVisibility(
                    visible = revealLowerCards,
                    enter = fadeIn(
                        tween(240, delayMillis = (index * 55).coerceAtMost(220))
                    ) + slideInVertically(
                        tween(280, delayMillis = (index * 55).coerceAtMost(220))
                    ) { height -> height / 10 } + scaleIn(
                        tween(280, delayMillis = (index * 55).coerceAtMost(220)),
                        initialScale = 0.985f
                    )
                ) {
                    when (cardId) {
                    "checkin", "recent7", "summary", "heatmap" -> {
                        RiseCard(modifier = Modifier.fillMaxWidth()) {
                            CheckinCard(
                                dayCounts = heatmapData,
                                weekCount = weekCount,
                                lastWeekCount = lastWeek,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                    "overview" -> {
                        RiseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MiniStat(stringResource(R.string.home_total_count), "$totalCount", CardBlue, Modifier.weight(1f))
                                    MiniStat(stringResource(R.string.home_month_count), "$monthCount", CardBlue, Modifier.weight(1f))
                                    MiniStat(
                                        stringResource(R.string.home_average_duration),
                                        formatNaturalDuration(
                                            avgDuration.toInt(),
                                            includeSeconds = false
                                        ),
                                        CardBlue,
                                        Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MiniStat(stringResource(R.string.home_week_volume), "${weekVol.toInt()}ml", CardTeal, Modifier.weight(1f))
                                    MiniStat(stringResource(R.string.home_max_distance), "${maxDistance.toInt()}cm", CardTeal, Modifier.weight(1f))
                                    MiniStat(
                                        stringResource(R.string.home_average_interval),
                                        averageIntervalDays?.let { "%.1f天".format(it) } ?: "—",
                                        CardTeal,
                                        Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    "length" -> {
                        val lengthData by vm.lengthRecords.collectAsStateWithLifecycle()
                        RiseCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate("length_history") }
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                HomeCardHeader(
                                    icon = Icons.Default.Straighten,
                                    title = stringResource(R.string.home_length_title),
                                    actionLabel = stringResource(R.string.home_view_all)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                if (lengthData.isNotEmpty()) {
                                    LengthTrendChart(
                                        records = lengthData,
                                        compact = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(164.dp)
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.home_length_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    "trend", "distance" -> {
                        val trendFlights by vm.trendFlights.collectAsStateWithLifecycle()
                        val selectedTrend by vm.selectedTrend.collectAsStateWithLifecycle()
                        val isVolume = selectedTrend == "volume"

                        val chartData = remember(trendFlights, selectedTrend) {
                            trendFlights.filter { f ->
                                val value =
                                    if (isVolume) f.semenVolumeMl else f.ejaculationDistanceCm
                                value != null && value.isFinite() && value >= 0f
                            }
                        }

                        RiseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                HomeCardHeader(
                                    icon = Icons.Default.Insights,
                                    title = stringResource(R.string.home_trend_title),
                                    trailing = {
                                        TrendSelector(
                                            isVolume = isVolume,
                                            onSelectVolume = {
                                                vm.selectedTrend.value = "volume"
                                            },
                                            onSelectDistance = {
                                                vm.selectedTrend.value = "distance"
                                            }
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                if (chartData.isNotEmpty()) {
                                    TrendColumnChart(
                                        flights = chartData,
                                        isVolume = isVolume,
                                        modifier = Modifier.fillMaxWidth().height(188.dp)
                                    )
                                } else {
                                    Text(
                                        if (isVolume) {
                                            stringResource(R.string.home_volume_empty)
                                        } else {
                                            stringResource(R.string.home_distance_empty)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    "achievement" -> {
                        val recentAchievements by vm.recentAchievements.collectAsStateWithLifecycle()
                        RiseCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navController.navigate("achievement_wall") }
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                HomeCardHeader(
                                    icon = Icons.Default.EmojiEvents,
                                    title = stringResource(R.string.home_achievement_title),
                                    actionLabel =
                                        if (recentAchievements.isNotEmpty()) {
                                            stringResource(R.string.home_view_all)
                                        }
                                        else null
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (recentAchievements.isEmpty()) {
                                    Text(
                                        stringResource(R.string.home_achievement_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        recentAchievements.take(3).forEach { achievement ->
                                            val (icon, _) = ACHIEVEMENT_ICONS[achievement.achievementKey] ?: ("🏆" to "")
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(icon, fontSize = 24.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    ACHIEVEMENT_ICONS[achievement.achievementKey]?.second ?: achievement.achievementKey,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
