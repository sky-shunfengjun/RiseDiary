package com.risediary.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatChartAxisLabel(labels: List<String>, value: Double): String {
    if (labels.isEmpty()) return "—"
    val requestedIndex = if (value.isFinite()) value.roundToInt() else 0
    return labels[requestedIndex.coerceIn(labels.indices)].ifBlank { "—" }
}

@Composable
internal fun rememberStartAxis(
    unit: String,
    step: Double,
) = VerticalAxis.rememberStart(
    line = null,
    label = rememberTextComponent(
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ),
    tick = null,
    valueFormatter = remember(unit, step) {
        CartesianValueFormatter { _, value, _ ->
            "${formatChartAxisValue(value, step)}$unit"
        }
    },
    guideline = rememberLineComponent(
        fill = Fill(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        thickness = 1.dp
    ),
    itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }) }
)

@Composable
internal fun rememberChartMarker(
    formatter: DefaultCartesianMarker.ValueFormatter
): DefaultCartesianMarker {
    val labelBackground = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f)),
        shape = RoundedCornerShape(12.dp),
        strokeFill = Fill(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        strokeThickness = 1.dp
    )
    val label = rememberTextComponent(
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        ),
        padding = Insets(horizontal = 10.dp, vertical = 7.dp),
        background = labelBackground,
        lineCount = 2
    )
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = formatter,
        indicatorSize = 8.dp,
        guideline = rememberLineComponent(
            fill = Fill(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
            thickness = 1.dp
        )
    )
}

private fun formatChartValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else "%.1f".format(Locale.getDefault(), value)

internal fun formatTrendMarkerValue(value: Double, unit: String): String =
    "${formatChartValue(value)} $unit"

internal fun formatLengthMarkerValue(erect: Double, flaccid: Double): String =
    "勃起 ${formatChartValue(erect)} cm\n疲软 ${formatChartValue(flaccid)} cm"

private fun formatChartAxisValue(value: Double, step: Double): String {
    val decimals = when {
        step >= 1.0 && step % 1.0 == 0.0 -> 0
        step >= 0.1 -> 1
        else -> 2
    }
    return "%.${decimals}f".format(Locale.getDefault(), value)
}

/** Achievement key → (icon, short name) map used across HomeScreen. */
val ACHIEVEMENT_ICONS = com.risediary.app.ui.achievement.AchievementCatalog.definitions
    .associate { it.key to (it.icon to it.shortName) }
