package com.risediary.app.ui.records

import com.risediary.app.util.formatNaturalDuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.R
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.TagJson
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.components.LiquidAlertDialog
import com.risediary.app.ui.components.liquidDialogCancelButtonColors
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.risediary.app.ui.icons.AppIcons

@Composable
fun RecordDetailScreen(
    flightId: Long,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val flight by viewModel.flight.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(flightId) { viewModel.load(flightId) }
    LaunchedEffect(deleted) {
        if (deleted) navigator.pop()
    }

    SecondaryPageScaffold(
        title = stringResource(R.string.record_detail_title),
        onBack = { navigator.pop() },
        actions = {
            flight?.let { value ->
                IconButton(
                    onClick = {
                        navigator.push(Route.RecordEdit(value.id))
                    }
                ) {
                    Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    ) { padding ->
        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            flight == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.record_detail_missing))
            }
            else -> DetailContent(
                flight = checkNotNull(flight),
                contentPadding = padding
            )
        }
    }

    if (showDeleteConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.record_detail_delete_dialog_title)) },
            text = { Text(stringResource(R.string.record_detail_delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.action_delete),
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
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
                    text = stringResource(R.string.action_cancel),
                    onClick = { showDeleteConfirm = false },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}

@Composable
private fun DetailContent(
    flight: Flight,
    contentPadding: androidx.compose.foundation.layout.PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(
                    stringResource(R.string.record_detail_start_time),
                    formatDateTime(flight.startTime)
                )
                DetailRow(
                    stringResource(R.string.record_detail_end_time),
                    formatDateTime(flight.endTime)
                )
                DetailRow(
                    stringResource(R.string.record_detail_duration),
                    formatNaturalDuration(flight.durationSeconds)
                )
                DetailRow(
                    stringResource(R.string.record_detail_volume),
                    stringResource(
                        R.string.record_detail_volume_value,
                        flight.semenVolumeMl ?: 0f,
                        flight.spurtCount ?: 0
                    )
                )
                flight.ejaculationDistanceCm?.let {
                    DetailRow(stringResource(R.string.record_detail_distance), "$it cm")
                }
            }
        }

        val tags = TagJson.decode(flight.methodTags)
        if (tags.isNotEmpty()) {
            Text(
                stringResource(R.string.record_detail_method_tags),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag -> DetailTagChip(tag) }
            }
        }

        if (flight.moodNote.isNotBlank()) {
            Text(
                stringResource(R.string.record_detail_note),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.SemiBold
            )
            RiseCard(modifier = Modifier.fillMaxWidth()) {
                Text(flight.moodNote, modifier = Modifier.padding(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun DetailTagChip(tag: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = tag,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value)
    }
}

private fun formatDateTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    )
