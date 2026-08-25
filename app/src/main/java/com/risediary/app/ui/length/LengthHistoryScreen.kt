package com.risediary.app.ui.length

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.ui.components.LiquidAddButton
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.components.liquidDialogConfirmButtonColors
import com.risediary.app.ui.components.LengthTrendChart
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import com.risediary.app.ui.theme.RiseCard
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

@Composable
fun LengthHistoryScreen(
    viewModel: LengthHistoryViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val records by viewModel.periodRecords.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editorRecord by remember { mutableStateOf<LengthRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LengthRecord?>(null) }

    SecondaryPageScaffold(
        title = stringResource(R.string.home_length_title),
        onBack = { navigator.pop() },
        floatingActionButton = { backdrop ->
            LiquidAddButton(
                onClick = {
                    editorRecord = null
                    showEditor = true
                },
                backdrop = backdrop,
                contentDescription = stringResource(R.string.length_add_record)
            )
        },
        reserveFloatingActionButtonSpace = false
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateLeftPadding(LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateRightPadding(LayoutDirection.Ltr),
                bottom = innerPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                                stringResource(R.string.length_empty),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            TextButton(
                                text = stringResource(R.string.length_add_first),
                                onClick = {
                                    editorRecord = null
                                    showEditor = true
                                },
                                colors = liquidDialogConfirmButtonColors()
                            )
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
                        stringResource(R.string.length_measurement_records),
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
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
            onDismiss = {
                showEditor = false
                viewModel.clearError()
            },
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
            title = { Text(stringResource(R.string.length_delete_dialog_title)) },
            text = { Text(stringResource(R.string.length_delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.action_delete),
                    onClick = {
                        viewModel.delete(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                        disabledColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledTextColor = MiuixTheme.colorScheme.error
                    )
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { deleteTarget = null },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}

@Composable
private fun ChartLegend() {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf(
            CardBlue to stringResource(R.string.length_legend_erect),
            CardGreen to stringResource(R.string.length_legend_flaccid)
        ).forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) { drawCircle(color, 5f, Offset(6f, 6f)) }
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
        title = {
            Text(
                if (record == null) {
                    stringResource(R.string.length_add_record)
                } else {
                    stringResource(R.string.length_edit_record)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = flaccid,
                    onValueChange = { if (it.isValidDecimal()) flaccid = it.take(6) },
                    label = stringResource(R.string.length_flaccid_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                TextField(
                    value = erect,
                    onValueChange = { if (it.isValidDecimal()) erect = it.take(6) },
                    label = stringResource(R.string.length_erect_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                TextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = stringResource(R.string.length_note_label),
                    maxLines = 3
                )
                if (error != null) Text(error, color = MiuixTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                text = stringResource(R.string.action_save),
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
                },
                colors = liquidDialogConfirmButtonColors()
            )
        },
        dismissButton = {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                colors = liquidDialogCancelButtonColors()
            )
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
                    stringResource(
                        R.string.length_record_summary,
                        record.erectLengthCm,
                        record.flaccidLengthCm
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MiuixTheme.colorScheme.error
                )
            }
        }
    }
}

private fun String.isValidDecimal(): Boolean =
    isEmpty() || matches(Regex("""\d{0,3}(\.\d{0,2})?"""))
