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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.risediary.app.ui.components.LiquidSnackbarTone
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.showLiquidSnackbar
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.risediary.app.ui.icons.AppIcons

@Composable
fun RecordsScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
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
    var showDatePicker by remember { mutableStateOf(false) }

    val pendingDeletions by viewModel.pendingDeletions.collectAsStateWithLifecycle()
    val currentPendingDeletion = pendingDeletions.firstOrNull()

    LaunchedEffect(currentPendingDeletion?.id, snackbarHostState) {
        if (currentPendingDeletion != null) {
            val result = snackbarHostState.showLiquidSnackbar(
                message = "已删除记录",
                actionLabel = "撤销",
                tone = LiquidSnackbarTone.UNDO
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(currentPendingDeletion)
            } else {
                viewModel.finalizeDeletion(currentPendingDeletion.id)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
            viewModel.clearPendingDeletions()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "记录",
            style = MiuixTheme.textStyles.title1.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            FilterTagRow(
                label = "全部标签",
                selected = viewModel.selectedTag == null,
                onClick = { viewModel.filterByTag(null) }
            )
            availableTagNames.forEach { tag ->
                FilterTagRow(
                    label = tag,
                    selected = viewModel.selectedTag == tag,
                    onClick = { viewModel.filterByTag(tag) }
                )
            }
            FilterTagRow(
                label =
                    if (viewModel.startDate == null) "日期范围"
                    else "${viewModel.startDate} 至 ${viewModel.endDate}",
                selected = viewModel.startDate != null,
                leadingIcon = {
                    Icon(
                        AppIcons.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = { showDatePicker = true }
            )
            if (viewModel.startDate != null) {
                TextButton(
                    text = "清除日期",
                    onClick = { viewModel.setDateRange(null, null) },
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        disabledColor = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.primary,
                        disabledTextColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val grouped = remember(flights) { groupByDate(flights) }
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
                        "暂无符合条件的起飞记录",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
private fun FilterTagRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.045f)
                }
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        leadingIcon?.invoke()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color =
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                            MiuixTheme.colorScheme.error.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = "删除",
                    tint = MiuixTheme.colorScheme.error
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
                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
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
                                "${flight.spurtCount ?: 0}股"
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
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确认删除这条记录吗？") },
            confirmButton = {
                TextButton(
                    text = "删除",
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        disabledColor = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                        disabledTextColor = MiuixTheme.colorScheme.error
                    )
                )
            },
            dismissButton = {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteConfirm = false },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}

@Composable
private fun StatItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(text, fontSize = MiuixTheme.textStyles.body2.fontSize)
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
