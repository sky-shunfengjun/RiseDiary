package com.risediary.app.ui.length

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.ui.components.LiquidAddButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.ui.components.LengthTrendChart
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import com.risediary.app.ui.theme.RiseCard
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LengthHistoryScreen(
    navController: NavController,
    viewModel: LengthHistoryViewModel = hiltViewModel()
) {
    val records by viewModel.periodRecords.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editorRecord by remember { mutableStateOf<LengthRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LengthRecord?>(null) }

    SecondaryPageScaffold(
        title = "长度追踪",
        onBack = { navController.navigateUp() },
        floatingActionButton = { backdrop ->
            LiquidAddButton(
                onClick = {
                    editorRecord = null
                    showEditor = true
                },
                backdrop = backdrop,
                contentDescription = "新增长度记录"
            )
        },
        reserveFloatingActionButtonSpace = false
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PeriodSegmentedControl(
                    selectedIndex = viewModel.selectedPeriod,
                    onSelected = viewModel::selectPeriod
                )
            }

            if (records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "暂无长度记录",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    editorRecord = null
                                    showEditor = true
                                }
                            ) { Text("添加第一条记录") }
                        }
                    }
                }
            } else {
                item {
                    RiseCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 16.dp
                            )
                        ) {
                            LengthTrendChart(
                                records = records,
                                compact = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(272.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ChartLegend()
                        }
                    }
                }
                item {
                    Text(
                        "测量记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(records.reversed(), key = LengthRecord::id) { record ->
                    LengthRecordCard(
                        record = record,
                        onEdit = {
                            editorRecord = record
                            showEditor = true
                        },
                        onDelete = { deleteTarget = record }
                    )
                }
            }
        }
    }

    if (showEditor) {
        LengthRecordDialog(
            record = editorRecord,
            error = error,
            onDismiss = { showEditor = false },
            onSave = {
                viewModel.save(it)
                if (
                    it.flaccidLengthCm in 0.1f..100f &&
                    it.erectLengthCm in 0.1f..100f
                ) {
                    showEditor = false
                }
            }
        )
    }

    deleteTarget?.let { target ->
        LiquidAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除长度记录") },
            text = { Text("确认删除这次测量吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        deleteTarget = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PeriodSegmentedControl(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    val currentTrackColor by rememberUpdatedState(trackColor)
    val backdrop = rememberLayerBackdrop { drawContent() }
    Box(
        modifier = Modifier
            // Preserve the compact visual size while reserving room for the
            // official liquid-glass shadow during long-press and drag.
            .width(264.dp)
            .height(72.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(232.dp)
                    .height(40.dp)
                    .clip(ContinuousCapsule)
                    .background(currentTrackColor)
            )
        }
        LiquidSegmentedControl(
            options = remember {
                listOf(
                    LiquidSegmentOption("30天", Icons.Default.DateRange),
                    LiquidSegmentOption("90天", Icons.Default.DateRange),
                    LiquidSegmentOption("全年", Icons.Default.DateRange)
                )
            },
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.Center)
                .width(232.dp),
            containerHeight = 40.dp,
            contentPadding = 3.dp,
            showIcons = false,
            labelFontSize = 12.sp
        )
    }
}

@Composable
private fun ChartLegend() {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf(CardBlue to "勃起长度", CardGreen to "松弛长度").forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) { drawCircle(color, 5f, Offset(6f, 6f)) }
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(20.dp))
        }
    }
}

@Composable
private fun LengthRecordDialog(
    record: LengthRecord?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (LengthRecord) -> Unit
) {
    var flaccid by remember(record) {
        mutableStateOf(record?.flaccidLengthCm?.toString().orEmpty())
    }
    var erect by remember(record) {
        mutableStateOf(record?.erectLengthCm?.toString().orEmpty())
    }
    var note by remember(record) { mutableStateOf(record?.note.orEmpty()) }

    LiquidAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (record == null) "新增长度记录" else "编辑长度记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = flaccid,
                    onValueChange = { if (it.isValidDecimal()) flaccid = it.take(6) },
                    label = { Text("疲软长度（cm）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = erect,
                    onValueChange = { if (it.isValidDecimal()) erect = it.take(6) },
                    label = { Text("勃起长度（cm）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("备注（可选）") },
                    maxLines = 3
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val todayStart = LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    onSave(
                        LengthRecord(
                            id = record?.id ?: 0L,
                            recordDate = record?.recordDate ?: todayStart,
                            flaccidLengthCm = flaccid.toFloatOrNull() ?: 0f,
                            erectLengthCm = erect.toFloatOrNull() ?: 0f,
                            note = note.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LengthRecordCard(
    record: LengthRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("yyyy/MM/dd", locale) }
    RiseCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dateFormat.format(Date(record.recordDate)))
                Text(
                    "勃起 ${record.erectLengthCm}cm · 疲软 ${record.flaccidLengthCm}cm",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun String.isValidDecimal(): Boolean =
    isEmpty() || matches(Regex("""\d{0,3}(\.\d{0,2})?"""))
