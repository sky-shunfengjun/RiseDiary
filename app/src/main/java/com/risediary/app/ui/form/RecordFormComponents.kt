package com.risediary.app.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.ui.components.LiquidSegmentedControl
import com.risediary.app.ui.components.LiquidSlider
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.risediary.app.util.PredictionQuantitySettings
import kotlin.math.roundToInt
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
internal fun FormSectionTitle(icon: ImageVector, title: String, subtitle: String? = null) {
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
            subtitle?.let {
                Text(
                    it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
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
    useEstimatedMode: Boolean,
    onSelectEstimatedMode: (Boolean) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labels = listOf(stringResource(R.string.form_volume_mode_estimated), stringResource(R.string.form_volume_mode_ml))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val textWidth = (constraints.maxWidth / 2 - with(density) { 44.dp.roundToPx() }).coerceAtLeast(1)
        val textHeight = labels.maxOf { label ->
            measurer.measure(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                constraints = Constraints(maxWidth = textWidth)).size.height
        }
        val controlHeight = maxOf(48.dp, with(density) { textHeight.toDp() } + 12.dp)
        Box(modifier = Modifier.fillMaxWidth().height(controlHeight + 16.dp)) {
            Box(modifier = Modifier.matchParentSize().layerBackdrop(backdrop)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 4.dp)
                    .height(controlHeight).clip(ContinuousCapsule)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.035f)))
            }
            LiquidSegmentedControl(
                options = listOf(
                    LiquidSegmentOption(labels[0], AppIcons.WaterDrop),
                    LiquidSegmentOption(labels[1], AppIcons.Numbers)
                ),
                selectedIndex = if (useEstimatedMode) 0 else 1,
                onSelected = { onSelectEstimatedMode(it == 0) },
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 4.dp),
                containerHeight = controlHeight,
                contentPadding = 3.dp,
                showIcons = true,
                labelFontSize = 13.sp,
                inlineIcon = true
            )
        }
    }
}

/** Only the selected branch remains interactive while the previous content fades out. */
@Composable
internal fun QuantityModeContent(
    useEstimatedMode: Boolean,
    estimatedContent: @Composable (active: Boolean) -> Unit,
    manualContent: @Composable (active: Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var previousMode by remember { mutableStateOf(useEstimatedMode) }
    LaunchedEffect(useEstimatedMode) {
        if (previousMode != useEstimatedMode) {
            focusManager.clearFocus()
            previousMode = useEstimatedMode
        }
    }
    AnimatedContent(targetState = useEstimatedMode,
        transitionSpec = {
            (EnterTransition.None togetherWith ExitTransition.None)
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(190) }))
        }, label = "quantity_mode_content") { estimated ->
        val active = estimated == useEstimatedMode
        val alpha by transition.animateFloat(
            transitionSpec = { tween(if (targetState == EnterExitState.Visible) 170 else 120) },
            label = "quantity_branch_alpha"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        Column(Modifier.fillMaxWidth().graphicsLayer {
            this.alpha = alpha
            compositingStrategy = CompositingStrategy.ModulateAlpha
            clip = false
        }.then(if (!active) Modifier.clearAndSetSemantics { } else Modifier),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (estimated) estimatedContent(active) else manualContent(active)
        }
    }
}

@Composable
internal fun PredictionVolumeSlider(
    ticks: Int,
    maximumTicks: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    val surface by rememberUpdatedState(
        if (LocalRiseDarkTheme.current) Color(0xFF20242B) else Color(0xF7FFFFFF)
    )
    val backdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().layerBackdrop(backdrop)) {
            Spacer(Modifier.height(48.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0 ml", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(
                    PredictionQuantitySettings.formatTicks(maximumTicks) + " ml",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        key(maximumTicks) {
            LiquidSlider(
                value = { ticks / 10f },
                onValueChange = { if (enabled) onValueChange((it * 10).roundToInt().coerceIn(0, maximumTicks)) },
                valueRange = 0f..maximumTicks / 10f,
                steps = maximumTicks - 1,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp),
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun QuickChoices(
    values: List<Int>,
    suffix: String,
    selected: Int?,
    onClick: (Int) -> Unit,
    enabled: Boolean = true
) {
    val selectedIndex = values.indexOfFirst { it == selected }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEachIndexed { index, value ->
            val isSelected = index == selectedIndex
            val accent = MiuixTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) accent else MiuixTheme.colorScheme.outline,
                        RoundedCornerShape(11.dp)
                    )
                    .clickable(enabled = enabled) { onClick(value) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
