package com.risediary.app.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.util.formatFormDuration
import java.time.Instant
import java.time.ZoneId
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
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
        pressFeedbackType = PressFeedbackType.Sink,
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
            pressFeedbackType = PressFeedbackType.Sink,
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
                        "用时",
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
                    contentDescription = "修改用时",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        if (hasLegacyDuration) {
            Text(
                "这是旧记录的原始用时；调整后最长可选 120 分钟。",
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
            options = remember {
                listOf(
                    LiquidSegmentOption("按毫升", AppIcons.WaterDrop),
                    LiquidSegmentOption("按股数", AppIcons.Numbers)
                )
            },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickChoices(
    values: List<Int>,
    suffix: String,
    selected: String,
    onClick: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            val isSelected = selected == "$value"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClick(value) }
                    .padding(end = 10.dp)
            ) {
                RadioButton(selected = isSelected, onClick = { onClick(value) })
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "$value$suffix",
                    style = MiuixTheme.textStyles.body2.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

internal fun formatDateOnly(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayOfWeek = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
    return "${date.monthValue}月${date.dayOfMonth}日 $dayOfWeek"
}

internal fun formatTimeOnly(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}
