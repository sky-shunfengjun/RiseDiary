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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.repository.TagJson
import com.risediary.app.ui.Screen
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

@Composable
fun RecordDetailScreen(
    navController: NavController,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val flight by viewModel.flight.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(deleted) {
        if (deleted) navController.popBackStack()
    }

    SecondaryPageScaffold(
        title = "记录详情",
        onBack = { navController.navigateUp() },
        actions = {
            flight?.let { value ->
                IconButton(
                    onClick = {
                        navController.navigate(Screen.RecordEdit.createRoute(value.id)) {
                            launchSingleTop = true
                        }
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
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
                Text("记录不存在或已被删除")
            }
            else -> DetailContent(
                flight = checkNotNull(flight),
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showDeleteConfirm) {
        LiquidAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除记录") },
            text = { Text("这条记录将被永久删除。") },
            confirmButton = {
                TextButton(
                    text = "删除",
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
                    text = "取消",
                    onClick = { showDeleteConfirm = false },
                    colors = liquidDialogCancelButtonColors()
                )
            }
        )
    }
}

@Composable
private fun DetailContent(flight: Flight, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow("开始时间", formatDateTime(flight.startTime))
                DetailRow("结束时间", formatDateTime(flight.endTime))
                DetailRow(
                    "用时",
                    formatNaturalDuration(flight.durationSeconds)
                )
                DetailRow("射精量", "${flight.semenVolumeMl ?: 0f} ml / ${flight.spurtCount ?: 0} 股")
                flight.ejaculationDistanceCm?.let { DetailRow("射精距离", "$it cm") }
            }
        }

        val tags = TagJson.decode(flight.methodTags)
        if (tags.isNotEmpty()) {
            Text(
                "方式标签",
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag -> DetailTagChip(tag) }
            }
        }

        if (flight.moodNote.isNotBlank()) {
            Text(
                "备注",
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
