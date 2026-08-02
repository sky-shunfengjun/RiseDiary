package com.risediary.app.ui.records

import com.risediary.app.util.formatNaturalDuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.repository.TagJson
import com.risediary.app.ui.Screen
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.LiquidDateRangePickerDialog
import com.risediary.app.ui.theme.RiseCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    navController: NavController,
    viewModel: RecordsViewModel = hiltViewModel()
) {
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.recentlyDeleted) {
        if (viewModel.recentlyDeleted != null) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除记录",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            else viewModel.clearDeletedReference()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "记录",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    onClick = { viewModel.filterByTag(null) },
                    label = { Text("全部标签") },
                    selected = viewModel.selectedTag == null
                )
                availableTagNames.forEach { tag ->
                    FilterChip(
                        onClick = { viewModel.filterByTag(tag) },
                        label = { Text(tag) },
                        selected = viewModel.selectedTag == tag
                    )
                }
                FilterChip(
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            if (viewModel.startDate == null) "日期范围"
                            else "${viewModel.startDate} 至 ${viewModel.endDate}"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                    selected = viewModel.startDate != null
                )
                if (viewModel.startDate != null) {
                    TextButton(onClick = { viewModel.setDateRange(null, null) }) {
                        Text("清除日期")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val grouped = remember(flights) { groupByDate(flights) }
            if (grouped.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FlightTakeoff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "暂无符合条件的起飞记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { group ->
                        item(key = group.header) {
                            Text(
                                group.header,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(group.items, key = Flight::id) { flight ->
                            FlightCard(
                                flight = flight,
                                onClick = {
                                    navController.navigate(
                                        Screen.RecordDetail.createRoute(flight.id)
                                    )
                                },
                                onDelete = { viewModel.delete(flight) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightCard(
    flight: Flight,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) showDeleteConfirm = true
            false
        },
        positionalThreshold = { distance -> distance * 0.4f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (
                            dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                            dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
                        ) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        RiseCard(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DATE_TIME_FORMAT.format(
                            Instant.ofEpochMilli(flight.startTime)
                                .atZone(ZoneId.systemDefault())
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatItem(Icons.Default.Timer, formatDuration(flight.durationSeconds))
                        StatItem(
                            Icons.Default.WaterDrop,
                            if (
                                RecordVolumeMode.fromStoredValue(flight.volumeInputMode) ==
                                RecordVolumeMode.SPURTS
                            ) {
                                "${flight.spurtCount ?: 0}股"
                            } else {
                                "${flight.semenVolumeMl ?: 0f}ml"
                            }
                        )
                        flight.ejaculationDistanceCm?.let {
                            StatItem(Icons.Default.Straighten, "${it}cm")
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
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("删除后可在提示条中撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDuration(seconds: Int): String =
    formatNaturalDuration(seconds)

private data class DateGroup(val header: String, val items: List<Flight>)

private fun groupByDate(flights: List<Flight>): List<DateGroup> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return flights
        .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, items) ->
            val header = when (date) {
                today -> "今天"
                today.minusDays(1) -> "昨天"
                else -> date.toString()
            }
            DateGroup(header, items.sortedByDescending(Flight::startTime))
        }
}


private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd  HH:mm")
