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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.components.CalendarHeatmap
import com.risediary.app.ui.components.CheckinCard
import com.risediary.app.ui.components.mainPageBottomSpacing
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons
import kotlinx.coroutines.delay

/** 返回主页后延迟再刷新，让 pop 转场动画先平稳结束。 */
private const val RETURN_REFRESH_SETTLE_MILLIS = 250L

private val pullRefreshThresholdSetter: java.lang.reflect.Method? by lazy {
    try {
        PullToRefreshState::class.java.getMethod(
            "setRefreshThresholdOffset\$top_yukonga_miuix_kmp_miuix_ui",
            Float::class.javaPrimitiveType
        )
    } catch (_: Throwable) {
        null
    }
}

/**
 * miuix 0.9.3 的下拉刷新触发阈值固定为屏高/24，日常浏览容易误触。
 * 通过内部 setter 抬高到屏高/12（约两倍），需要更刻意的下拉才会触发；
 * 反射失败时静默回退到库默认行为。
 */
private fun raisePullToRefreshThreshold(state: PullToRefreshState, windowHeightPx: Float) {
    if (windowHeightPx <= 0f) return
    try {
        pullRefreshThresholdSetter?.invoke(state, windowHeightPx * (1f / 6f) * (1f / 2f))
    } catch (_: Throwable) {
        // 保持库默认阈值
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val scrollState = rememberScrollState()

    // Refresh on first composition (with spinner) and whenever we return to the
    // main page (silently, after the pop transition settles, so the reload work
    // does not compete with the animation frame budget).
    var hasLoadedOnce by rememberSaveable { mutableStateOf(false) }
    val currentRoute = navigator.current()
    LaunchedEffect(currentRoute) {
        if (currentRoute is Route.Main) {
            if (!hasLoadedOnce) {
                vm.refresh()
                hasLoadedOnce = true
            } else {
                delay(RETURN_REFRESH_SETTLE_MILLIS)
                vm.refresh(silent = true)
            }
        }
    }

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
    val dateStr = SimpleDateFormat(
        stringResource(R.string.home_date_pattern),
        Locale.CHINESE
    ).format(Date())

    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()

    val bottomSpacing = mainPageBottomSpacing()

    val pullToRefreshState = rememberPullToRefreshState()
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    SideEffect {
        raisePullToRefreshThreshold(pullToRefreshState, windowHeightPx)
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { vm.refresh() },
        pullToRefreshState = pullToRefreshState,
        refreshTexts = listOf(
            stringResource(R.string.home_refresh_pull),
            stringResource(R.string.home_refresh_release),
            stringResource(R.string.home_refresh_refreshing),
            stringResource(R.string.home_refresh_done)
        ),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = bottomSpacing),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = dateStr,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                stringResource(vm.getGreeting(), username),
                style = MiuixTheme.textStyles.headline1.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val isDarkCard = LocalRiseDarkTheme.current
            val statusRecorded = todayStatus.hasRecords
            val cardBg = when {
                statusRecorded && !isDarkCard -> Color(0xFFDFFAE4)
                statusRecorded -> Color(0xFF1B3A2A)
                !isDarkCard -> Color(0xFFD9E9FF)
                else -> Color(0xFF1A3356)
            }
            val cardAccent = when {
                statusRecorded -> Color(0xFF36D167)
                !isDarkCard -> Color(0xFF0A84FF)
                else -> Color(0xFF5CA8FF)
            }
            val cardText = if (isDarkCard) Color(0xFFF2F2F2) else Color(0xFF111111)
            val statusIcon = if (statusRecorded) AppIcons.CheckCircle else AppIcons.FlightTakeoffLite
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = cardAccent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 48.dp, y = 42.dp)
                        .size(176.dp)
                )
                val daysAgo = lastFlightDaysAgo
                if (!statusRecorded && daysAgo != null) {
                    Text(
                        when (daysAgo) {
                            0 -> stringResource(R.string.home_last_record_today)
                            1 -> stringResource(R.string.home_last_record_yesterday)
                            else -> stringResource(
                                R.string.home_last_record_days,
                                daysAgo
                            )
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = cardText.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 20.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .padding(end = 132.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text =
                                if (statusRecorded) {
                                    stringResource(R.string.home_status_today)
                                } else {
                                    stringResource(R.string.home_status_ready)
                                },
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.SemiBold,
                            color = cardText.copy(alpha = 0.78f)
                        )
                        Text(
                            todayStatusText,
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold,
                            color = cardText,
                            maxLines = 2
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = AppIcons.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = cardAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.home_tip_label),
                                style = MiuixTheme.textStyles.footnote2,
                                color = cardAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            stringResource(dailyTipResId),
                            style = MiuixTheme.textStyles.body2,
                            color = cardText.copy(alpha = 0.72f),
                            maxLines = 2
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.home_overview_title),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
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
                        RiseCard(
                            modifier = Modifier.fillMaxWidth(),
                            allowContentOverflow = true
                        ) {
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
                                        averageIntervalDays?.let {
                                            stringResource(R.string.home_interval_days, it)
                                        } ?: "—",
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
                            onClick = { navigator.push(Route.LengthHistory) }
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                HomeCardHeader(
                                    icon = AppIcons.Straighten,
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
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                                    icon = AppIcons.Insights,
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
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }
                    }
                    "achievement" -> {
                        val recentAchievements by vm.recentAchievements.collectAsStateWithLifecycle()
                        RiseCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navigator.push(Route.AchievementWall) }
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                HomeCardHeader(
                                    icon = AppIcons.EmojiEvents,
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
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        recentAchievements.take(3).forEach { achievement ->
                                            val (icon, _) = ACHIEVEMENT_ICONS[achievement.achievementKey] ?: ("🏆" to 0)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(icon, fontSize = 24.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val shortNameRes =
                                                    ACHIEVEMENT_ICONS[achievement.achievementKey]?.second
                                                Text(
                                                    shortNameRes?.let { stringResource(it) }
                                                        ?: achievement.achievementKey,
                                                    style = MiuixTheme.textStyles.footnote2,
                                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
    }
}
