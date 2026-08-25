package com.risediary.app.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.util.formatFormDuration
import java.time.Instant
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.risediary.app.ui.icons.AppIcons

@Composable
internal fun FormSectionTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
internal fun CompactValueButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f)
    Card(
        modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        cornerRadius = 14.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        ),
        pressFeedbackType = PressFeedbackType.None,
        showIndication = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(text, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
internal fun DurationValueButton(
    durationSeconds: Int,
    hasLegacyDuration: Boolean,
    onClick: () -> Unit
) {
    val borderColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.075f)
            ),
            pressFeedbackType = PressFeedbackType.None,
            showIndication = true,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AppIcons.Schedule, null, tint = MiuixTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.form_duration_label),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        formatFormDuration(durationSeconds),
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    AppIcons.ChevronRight,
                    contentDescription = stringResource(R.string.form_change_duration),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        if (hasLegacyDuration) {
            Text(
                stringResource(R.string.form_legacy_duration_note),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
internal fun VolumeModeSelector(
    useSpurtMode: Boolean,
    onSelectSpurtMode: (Boolean) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(40.dp)
                .clip(ContinuousCapsule)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                .layerBackdrop(backdrop)
        )
        LiquidSegmentedControl(
            options = listOf(
                LiquidSegmentOption(stringResource(R.string.form_volume_mode_ml), AppIcons.WaterDrop),
                LiquidSegmentOption(stringResource(R.string.form_volume_mode_spurts), AppIcons.Numbers)
            ),
            selectedIndex = if (useSpurtMode) 1 else 0,
            onSelected = { onSelectSpurtMode(it == 1) },
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            containerHeight = 40.dp,
            contentPadding = 3.dp,
            showIcons = false,
            labelFontSize = 13.sp
        )
    }
}

@Composable
internal fun QuickChoices(
    values: List<Int>,
    suffix: String,
    selected: Int?,
    onClick: (Int) -> Unit
) {
    val selectedIndex = values.indexOfFirst { it == selected }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.forEachIndexed { index, value ->
            val isSelected = index == selectedIndex
            val accent = MiuixTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) accent else MiuixTheme.colorScheme.outline,
                        RoundedCornerShape(11.dp)
                    )
                    .clickable { onClick(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$value$suffix",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}

internal fun formatDateOnly(context: android.content.Context, millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val weekdayLabels = context.resources.getStringArray(R.array.weekday_labels)
    val dayOfWeek = context.getString(
        R.string.form_weekday_format,
        weekdayLabels[date.dayOfWeek.value - 1]
    )
    return context.getString(
        R.string.form_date_with_weekday,
        date.monthValue,
        date.dayOfMonth,
        dayOfWeek
    )
}

internal fun formatTimeOnly(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}
