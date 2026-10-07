package com.risediary.app.ui.records

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.risediary.app.R
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.repository.TagJson
import com.risediary.app.ui.components.*
import com.risediary.app.ui.form.FormSectionTitle
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseCard
import com.risediary.app.util.RecordQuantityDisplay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordDetailContent(
    flight: Flight,
    contentPadding: PaddingValues,
    scrollState: ScrollState,
    error: String?,
    busy: Boolean,
    onRetry: () -> Unit,
    videoContent: (@Composable () -> Unit)?
) {
    val calendar = LocalCalendarEnvironment.current
    var retainedError by remember { mutableStateOf(error) }
    SideEffect { error?.let { retainedError = it } }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RecordDetailSummary(flight)
        videoContent?.invoke()
        RiseCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                FormSectionTitle(AppIcons.Schedule, stringResource(R.string.record_detail_time_details))
                DetailTimeValue(stringResource(R.string.record_detail_start_time), formatDetailDateTime(flight.startTime, calendar.zoneId))
                DetailTimeValue(stringResource(R.string.record_detail_end_time), formatDetailDateTime(flight.endTime, calendar.zoneId))
                flight.ejaculationDistanceCm?.let {
                    DetailTimeValue(stringResource(R.string.record_detail_distance), "$it cm")
                }
            }
        }
        val tags = TagJson.decode(flight.methodTags)
        if (tags.isNotEmpty()) {
            RiseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormSectionTitle(AppIcons.LocalOffer, stringResource(R.string.record_detail_method_tags))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { DetailTagChip(it) } }
                }
            }
        }
        if (flight.moodNote.isNotBlank()) {
            RiseCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormSectionTitle(AppIcons.Notes, stringResource(R.string.record_detail_note))
                    Text(flight.moodNote, lineHeight = 26.sp)
                }
            }
        }
        AnimatedUiVisibility(error != null) { active ->
            InlineStatusContent(listOfNotNull(error ?: retainedError), Modifier.padding(8.dp),
                onRetry = onRetry, enabled = active && !busy)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun RecordDetailSummary(flight: Flight, modifier: Modifier = Modifier) {
    val calendar = LocalCalendarEnvironment.current
    val date = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)
        .format(Instant.ofEpochMilli(flight.startTime).atZone(calendar.zoneId))
    val seconds = flight.durationSeconds.coerceAtLeast(0)
    val duration = buildAnnotatedString {
        append((seconds / 60).toString())
        withStyle(SpanStyle(fontSize = 14.sp)) { append(" 分 ") }
        append((seconds % 60).toString().padStart(2, '0'))
        withStyle(SpanStyle(fontSize = 14.sp)) { append(" 秒") }
    }
    val quantity = buildAnnotatedString {
        val volume = flight.semenVolumeMl
        if (volume == null) withStyle(SpanStyle(fontSize = 14.sp)) { append(RecordQuantityDisplay.current(flight)) }
        else {
            if (flight.volumeInputMode == RecordVolumeMode.ESTIMATED.storedValue) {
                withStyle(SpanStyle(fontSize = 14.sp)) { append("约 ") }
            }
            append(BigDecimal(volume.toString()).stripTrailingZeros().toPlainString())
            withStyle(SpanStyle(fontSize = 14.sp)) { append(" 毫升") }
        }
    }
    val colors = MiuixTheme.colorScheme
    val style = TextStyle(color = colors.onSurface, fontSize = 34.sp, fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val desiredWidth = with(density) {
        (maxOf(measurer.measure(duration, style).size.width, measurer.measure(quantity, style).size.width) * 2).toDp()
    } + 20.dp
    RiseCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.CalendarMonth, null, Modifier.size(20.dp), tint = colors.primary)
                Text(date, color = colors.onSurfaceVariantSummary, fontSize = 15.sp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < maxOf(desiredWidth, with(density) { 264.dp * fontScale })
                val durationContent: @Composable () -> Unit = {
                    DetailMetric(AppIcons.Timer, stringResource(R.string.record_detail_duration)) {
                        BasicText(duration, style = style, modifier = Modifier.testTag("detail_duration"))
                    }
                }
                val quantityContent: @Composable () -> Unit = {
                    DetailMetric(AppIcons.WaterDrop, stringResource(R.string.record_detail_volume)) {
                        BasicText(quantity, style = style, modifier = Modifier.testTag("detail_quantity"))
                    }
                }
                if (stacked) Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    durationContent(); quantityContent()
                } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.weight(1f)) { durationContent() }
                    Box(Modifier.weight(1f)) { quantityContent() }
                }
            }
            RecordQuantityDisplay.original(flight)?.let { original ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.record_detail_original_quantity), fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary)
                    Text(original, fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(icon: ImageVector, label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(MiuixTheme.colorScheme.primary.copy(alpha = 0.09f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
            }
            Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        content()
    }
}

@Composable
private fun DetailTimeValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, fontSize = 16.sp)
    }
}

@Composable
internal fun DetailTagChip(tag: String) {
    Box(Modifier.background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(tag, fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.primary)
    }
}

internal fun formatDetailDateTime(epochMillis: Long, zoneId: ZoneId): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))
