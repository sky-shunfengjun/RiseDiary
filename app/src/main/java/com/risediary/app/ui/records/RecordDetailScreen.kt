package com.risediary.app.ui.records

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.risediary.app.media.VideoAccessState
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.components.rememberTopBlurProgress
import androidx.compose.foundation.ScrollState
import com.risediary.app.util.formatRecordDuration
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
import com.risediary.app.media.localVideoRef
import com.risediary.app.ui.video.VideoAttachmentCard
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
import com.risediary.app.ui.components.LocalCalendarEnvironment
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

    val videoAccess by viewModel.videoAccess.collectAsStateWithLifecycle()
    val videoBusy by viewModel.videoBusy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val active = LocalPageEffectsActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) viewModel.selectVideo(uri.toString(), data.flags)
        else viewModel.cancelVideoSelection()
    }
    fun pickVideo() {
        if (!viewModel.beginVideoSelection()) return
        try {
            videoPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        } catch (_: Exception) { viewModel.cancelVideoSelection() }
    }

    LaunchedEffect(flightId, active) { if (active) viewModel.load(flightId) }
    DisposableEffect(lifecycle, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (active && event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(deleted) {
        if (deleted) navigator.pop()
    }

    val scrollState = rememberScrollState()

    SecondaryPageScaffold(
        topBlurProgress = if (!loading && flight != null) rememberTopBlurProgress(scrollState) else { { 0f } },
        title = stringResource(R.string.record_detail_title),
        onBack = { navigator.pop() },
        actions = {
            flight?.let { value ->
                IconButton(
                    enabled = !videoBusy && !loading,
                    onClick = {
                        navigator.push(Route.RecordEdit(value.id))
                    }
                ) {
                    Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                }
                IconButton(onClick = { showDeleteConfirm = true }, enabled = !videoBusy && !loading) {
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
                Text(error ?: stringResource(R.string.record_detail_missing))
                if (error != null) TextButton(text = stringResource(R.string.action_retry), onClick = viewModel::refresh)
            }
            else -> DetailContent(
                flight = checkNotNull(flight),
                contentPadding = padding,
                scrollState = scrollState,
                videoAccess = videoAccess,
                videoBusy = videoBusy,
                error = error,
                onRelink = ::pickVideo,
                onRetry = viewModel::refresh
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
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    scrollState: ScrollState,
    videoAccess: VideoAccessState?,
    videoBusy: Boolean,
    error: String?,
    onRelink: () -> Unit,
    onRetry: () -> Unit
) {
    val calendar = LocalCalendarEnvironment.current
    val navigator = LocalNavigator.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                    formatDateTime(flight.startTime, calendar.zoneId)
                )
                DetailRow(
                    stringResource(R.string.record_detail_end_time),
                    formatDateTime(flight.endTime, calendar.zoneId)
                )
                DetailRow(
                    stringResource(R.string.record_detail_duration),
                    formatRecordDuration(flight.durationSeconds)
                )
                DetailRow(
                    stringResource(R.string.record_detail_volume),
                    com.risediary.app.util.RecordQuantityDisplay.current(flight)
                )
                com.risediary.app.util.RecordQuantityDisplay.original(flight)?.let {
                    DetailRow(stringResource(R.string.record_detail_original_quantity), it)
                }
                flight.ejaculationDistanceCm?.let {
                    DetailRow(stringResource(R.string.record_detail_distance), "$it cm")
                }
            }
        }

        flight.localVideoRef()?.let { video ->
            VideoAttachmentCard(video = video,
                onPlay = { navigator.push(Route.RecordVideo(flight.id)) },
                onSelect = if (videoAccess != null && videoAccess != VideoAccessState.READABLE) onRelink else null,
                selectLabel = stringResource(R.string.video_relink),
                playEnabled = videoAccess == VideoAccessState.READABLE,
                unavailable = videoAccess != null && videoAccess != VideoAccessState.READABLE,
                busy = videoBusy, error = error)
        }
        if (error != null) {
            if (flight.localVideoRef() == null) Text(error, color = MiuixTheme.colorScheme.error)
            TextButton(text = stringResource(R.string.action_retry), onClick = onRetry, enabled = !videoBusy)
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

private fun formatDateTime(epochMillis: Long, zoneId: ZoneId): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(
        Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    )
