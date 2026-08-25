package com.risediary.app.ui.records

import com.risediary.app.util.formatNaturalDuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.repository.TagJson
import com.risediary.app.ui.components.LiquidDateRangePickerDialog
import com.risediary.app.ui.components.LiquidSnackbarTone
import com.risediary.app.ui.components.showLiquidSnackbar
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.risediary.app.ui.icons.AppIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RecordsScreen(
    snackbarHostState: SnackbarHostState,
    snackbarScope: CoroutineScope,
    viewModel: RecordsViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val currentTags by viewModel.tags.collectAsStateWithLifecycle()
    val allFlights by viewModel.allFlights.collectAsStateWithLifecycle()
    val flights = remember(
        allFlights,
        viewModel.selectedTag,
        viewModel.startDate,
        viewModel.endDate
    ) {
        viewModel.filter(allFlights)
    }
    val availableTagNames = remember(currentTags, allFlights) {
        (currentTags.map { it.name } + allFlights.flatMap { TagJson.decode(it.methodTags) })
            .distinct()
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val hasActiveFilter = viewModel.selectedTag != null || viewModel.startDate != null

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = statusBarTop + 16.dp,
                    end = 20.dp,
                    bottom = 16.dp
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.records_title),
                    style = MiuixTheme.textStyles.title1.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showFilterMenu = true }) {
                    Icon(
                        AppIcons.Filter,
                        contentDescription = stringResource(R.string.records_filter),
                        tint =
                            if (hasActiveFilter) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
            OverlayListPopup(
                show = showFilterMenu,
                onDismissRequest = { showFilterMenu = false },
                alignment = PopupPositionProvider.Align.End
            ) {
                ListPopupColumn {
                    FilterMenuRow(
                        label = stringResource(R.string.records_filter_all_tags),
                        icon = AppIcons.List,
                        selected = viewModel.selectedTag == null,
                        onClick = {
                            viewModel.filterByTag(null)
                            showFilterMenu = false
                        }
                    )
                    availableTagNames.forEach { tag ->
                        FilterMenuRow(
                            label = tag,
                            icon = AppIcons.LocalOffer,
                            selected = viewModel.selectedTag == tag,
                            onClick = {
                                viewModel.filterByTag(tag)
                                showFilterMenu = false
                            }
                        )
                    }
                    FilterMenuRow(
                        label =
                            if (viewModel.startDate == null) {
                                stringResource(R.string.records_pick_date_range)
                            } else {
                                stringResource(
                                    R.string.records_date_range_format,
                                    viewModel.startDate.toString(),
                                    viewModel.endDate.toString()
                                )
                            },
                        icon = AppIcons.CalendarMonth,
                        selected = viewModel.startDate != null,
                        onClick = {
                            showDatePicker = true
                            showFilterMenu = false
                        }
                    )
                    if (hasActiveFilter) {
                    FilterMenuRow(
                        label = stringResource(R.string.records_clear_filter),
                        icon = AppIcons.Close,
                            onClick = {
                                viewModel.filterByTag(null)
                                viewModel.setDateRange(null, null)
                                showFilterMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val todayLabel = stringResource(R.string.records_today)
        val yesterdayLabel = stringResource(R.string.records_yesterday)
        val grouped = remember(flights, viewModel.userZoneId, todayLabel, yesterdayLabel) {
            groupByDate(flights, viewModel.userZoneId, todayLabel, yesterdayLabel)
        }
        if (grouped.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        AppIcons.FlightTakeoff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.records_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                grouped.forEach { group ->
                    item(key = group.header) {
                        Text(
                            group.header,
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(group.items, key = Flight::id) { flight ->
                        val deletedMessage = stringResource(R.string.records_deleted)
                        val undoAction = stringResource(R.string.action_undo)
                        FlightCard(
                            flight = flight,
                            zoneId = viewModel.userZoneId,
                            onClick = {
                                navigator.push(Route.RecordDetail(flight.id))
                            },
                            onLongClick = {
                                viewModel.delete(flight)
                                // Launched on the stable MainAppContent scope so the
                                // undo Snackbar survives navigation to detail/edit.
                                snackbarScope.launch {
                                    val result = snackbarHostState.showLiquidSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoAction,
                                        tone = LiquidSnackbarTone.UNDO
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete(flight)
                                    } else {
                                        viewModel.finalizeDeletion(flight.id)
                                    }
                                }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (showDatePicker) {
        LiquidDateRangePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onConfirm = { start, end ->
                viewModel.setDateRange(start, end)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun FilterMenuRow(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint =
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantActions
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color =
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (selected) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FlightCard(
    flight: Flight,
    zoneId: ZoneId,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    RiseCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DATE_TIME_FORMAT.format(
                        Instant.ofEpochMilli(flight.startTime)
                            .atZone(zoneId)
                    ),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatItem(AppIcons.Timer, formatDuration(flight.durationSeconds))
                    StatItem(
                        AppIcons.WaterDrop,
                        if (
                            RecordVolumeMode.fromStoredValue(flight.volumeInputMode) ==
                            RecordVolumeMode.SPURTS
                        ) {
                            stringResource(R.string.records_spurts_format, flight.spurtCount ?: 0)
                        } else {
                                "${flight.semenVolumeMl ?: 0f}ml"
                            }
                        )
                        flight.ejaculationDistanceCm?.let {
                            StatItem(AppIcons.Straighten, "${it}cm")
                        }
                    }
                    val tags = TagJson.decode(flight.methodTags)
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            tags.forEach { tag ->
                                DetailTagChip(tag)
                            }
                        }
                    }
                }
                Icon(
                    AppIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        }
    }

@Composable
private fun StatItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(text, fontSize = MiuixTheme.textStyles.body1.fontSize)
    }
}

private fun formatDuration(seconds: Int): String =
    formatNaturalDuration(seconds)

private data class DateGroup(val header: String, val items: List<Flight>)

private fun groupByDate(
    flights: List<Flight>,
    zoneId: ZoneId,
    todayLabel: String,
    yesterdayLabel: String
): List<DateGroup> {
    val zone = zoneId
    val today = LocalDate.now(zone)
    return flights
        .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, items) ->
            val header = when (date) {
                today -> todayLabel
                today.minusDays(1) -> yesterdayLabel
                else -> date.toString()
            }
            DateGroup(header, items.sortedByDescending(Flight::startTime))
        }
}


private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd  HH:mm")
