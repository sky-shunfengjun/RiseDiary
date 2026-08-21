package com.risediary.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class TrendChartPoint(
    val x: Int,
    val value: Float,
    val axisLabel: String,
    val markerLabel: String
)

internal data class LengthChartPoint(
    val x: Int,
    val erect: Float,
    val flaccid: Float,
    val axisLabel: String,
    val markerLabel: String
)

internal data class ChartAxisRange(
    val minY: Double,
    val maxY: Double,
    val step: Double,
)

internal fun buildTrendChartPoints(
    flights: List<Flight>,
    isVolume: Boolean,
    locale: Locale = Locale.getDefault()
): List<TrendChartPoint> {
    val sorted = flights
        .mapNotNull { flight ->
            val value = if (isVolume) flight.semenVolumeMl else flight.ejaculationDistanceCm
            if (value == null || !value.isFinite() || value < 0f) null else flight to value
        }
        .sortedWith(compareBy<Pair<Flight, Float>> { it.first.startTime }.thenBy { it.first.id })
    val dayFormat = SimpleDateFormat("M/d", locale)
    val timeFormat = SimpleDateFormat("M/d HH:mm", locale)
    val dayCounts = sorted.groupingBy {
        dayFormat.format(Date(it.first.startTime))
    }.eachCount()
    return sorted.map { (flight, value) ->
        val day = dayFormat.format(Date(flight.startTime))
        val marker = timeFormat.format(Date(flight.startTime))
        TrendChartPoint(
            x = 0,
            value = value,
            axisLabel =
                if ((dayCounts[day] ?: 0) > 1) {
                    marker.replace(" ", "\n")
                } else {
                    day
                },
            markerLabel = marker
        )
    }.mapIndexed { index, point -> point.copy(x = index) }
}

internal fun buildLengthChartPoints(
    records: List<LengthRecord>,
    locale: Locale = Locale.getDefault()
): List<LengthChartPoint> {
    val valid = records
        .filter {
            it.erectLengthCm.isFinite() && it.flaccidLengthCm.isFinite() &&
                it.erectLengthCm >= 0f && it.flaccidLengthCm >= 0f
        }
        .sortedWith(compareBy<LengthRecord> { it.recordDate }.thenBy { it.id })
    val dayFormat = SimpleDateFormat("M/d", locale)
    val fullFormat = SimpleDateFormat("yyyy/M/d", locale)
    val dayIndexes = mutableMapOf<String, Int>()
    val dayCounts = valid.groupingBy { dayFormat.format(Date(it.recordDate)) }.eachCount()
    return valid.mapIndexed { index, record ->
        val day = dayFormat.format(Date(record.recordDate))
        val occurrence = (dayIndexes[day] ?: 0) + 1
        dayIndexes[day] = occurrence
        val suffix = if ((dayCounts[day] ?: 0) > 1) " · $occurrence" else ""
        LengthChartPoint(
            x = index,
            erect = record.erectLengthCm,
            flaccid = record.flaccidLengthCm,
            axisLabel = "$day$suffix",
            markerLabel = "${fullFormat.format(Date(record.recordDate))}$suffix"
        )
    }
}

internal fun trendAxisRange(values: List<Double>): ChartAxisRange {
    val maximum = values.filter(Double::isFinite).maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    if (maximum == 0.0) return ChartAxisRange(0.0, 1.0, 0.25)
    val step = niceChartStep(maximum / 4.0)
    val roundedMaximum = ceil(maximum / step) * step
    val maxY =
        if (roundedMaximum <= maximum + 0.000_001) {
            roundedMaximum + step
        } else {
            roundedMaximum
        }
    return ChartAxisRange(0.0, maxY, step)
}

internal fun lengthAxisRange(values: List<Double>): ChartAxisRange {
    val finiteValues = values.filter(Double::isFinite)
    if (finiteValues.isEmpty()) return ChartAxisRange(0.0, 1.0, 0.25)
    val rawMin = finiteValues.min()
    val rawMax = finiteValues.max()
    val padding = (rawMax - rawMin).coerceAtLeast(1.0) * 0.12
    val paddedMin = (rawMin - padding).coerceAtLeast(0.0)
    val paddedMax = rawMax + padding
    val step = niceChartStep((paddedMax - paddedMin) / 4.0)
    val minY = floor(paddedMin / step) * step
    val maxY = ceil(paddedMax / step) * step
    return ChartAxisRange(
        minY = minY,
        maxY = maxY.coerceAtLeast(minY + step),
        step = step
    )
}

private fun niceChartStep(rawStep: Double): Double {
    if (!rawStep.isFinite() || rawStep <= 0.0) return 1.0
    val exponent = floor(log10(rawStep))
    val magnitude = 10.0.pow(exponent)
    val fraction = rawStep / magnitude
    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceFraction * magnitude
}

@Composable
fun TrendColumnChart(
    flights: List<Flight>,
    isVolume: Boolean,
    modifier: Modifier = Modifier
) {
    ChartTheme {
        TrendColumnChartContent(flights, isVolume, modifier)
    }
}

@Composable
private fun TrendColumnChartContent(
    flights: List<Flight>,
    isVolume: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val points = remember(flights, isVolume, locale) {
        buildTrendChartPoints(flights, isVolume, locale)
    }
    if (points.isEmpty()) return
    val unit = if (isVolume) "ml" else "cm"
    val color = if (isVolume) CardGreen else CardBlue
    val modelProducer = remember { CartesianChartModelProducer() }
    val range = remember(points) {
        trendAxisRange(points.map { it.value.toDouble() })
    }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            columnModel {
                series(
                    x = points.map(TrendChartPoint::x),
                    y = points.map(TrendChartPoint::value),
                    key = if (isVolume) "volume" else "distance"
                )
            }
        }
    }

    val axisLabels = remember(points) { points.map(TrendChartPoint::axisLabel) }
    val axisFormatter = remember(axisLabels) {
        CartesianValueFormatter { _, value, _ ->
            formatChartAxisLabel(axisLabels, value)
        }
    }
    val theme = rememberM3VicoTheme(
        columnCartesianLayerColors = listOf(color),
        lineCartesianLayerColors = listOf(CardBlue, CardGreen),
        lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        textColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ProvideVicoTheme(theme) {
        val column = rememberLineComponent(
            fill = Fill(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = 0.48f),
                        color.copy(alpha = 0.82f),
                        color
                    )
                )
            ),
            thickness = 20.dp,
            shape = RoundedCornerShape(10.dp)
        )
        val layer = rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
            columnCollectionSpacing = 20.dp,
            rangeProvider = remember(range) {
                CartesianLayerRangeProvider.fixed(
                    minY = range.minY,
                    maxY = range.maxY
                )
            }
        )
        val marker = rememberChartMarker(
            formatter = remember(points, unit) {
                DefaultCartesianMarker.ValueFormatter { _, targets ->
                    val target = targets.firstOrNull() as? ColumnCartesianLayerMarkerTarget
                    val x = target?.x?.roundToInt()
                    val point = x?.let(points::getOrNull)
                    val value = target?.columns?.firstOrNull()?.entry?.y
                    if (point == null || value == null) {
                        ""
                    } else {
                        formatTrendMarkerValue(value, unit)
                    }
                }
            }
        )
        val bottomLabel = rememberTextComponent(
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            lineCount = 2
        )
        val chart = rememberCartesianChart(
            layer,
            startAxis = rememberStartAxis(unit, range.step),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null,
                label = bottomLabel,
                tick = null,
                guideline = null,
                valueFormatter = axisFormatter,
                itemPlacer = remember {
                    HorizontalAxis.ItemPlacer.aligned(
                        addExtremeLabelPadding = true
                    )
                }
            ),
            marker = marker
        )
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(
                scrollEnabled = points.size > 7,
                initialScroll = Scroll.Absolute.End
            ),
            zoomState = rememberVicoZoomState(zoomEnabled = false)
        )
    }
}

@Composable
fun LengthTrendChart(
    records: List<LengthRecord>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    ChartTheme {
        LengthTrendChartContent(records, compact, modifier)
    }
}

@Composable
private fun LengthTrendChartContent(
    records: List<LengthRecord>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val points = remember(records, locale) { buildLengthChartPoints(records, locale) }
    if (points.isEmpty()) return
    val modelProducer = remember { CartesianChartModelProducer() }
    val range = remember(points) {
        lengthAxisRange(
            points.flatMap { listOf(it.erect.toDouble(), it.flaccid.toDouble()) }
        )
    }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel {
                val xValues = points.map(LengthChartPoint::x)
                series(xValues, points.map(LengthChartPoint::erect), key = "erect")
                series(xValues, points.map(LengthChartPoint::flaccid), key = "flaccid")
            }
        }
    }

    val axisLabels = remember(points) { points.map(LengthChartPoint::axisLabel) }
    val axisFormatter = remember(axisLabels) {
        CartesianValueFormatter { _, value, _ ->
            formatChartAxisLabel(axisLabels, value)
        }
    }
    val theme = rememberM3VicoTheme(
        columnCartesianLayerColors = listOf(CardBlue, CardGreen),
        lineCartesianLayerColors = listOf(CardBlue, CardGreen),
        lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        textColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ProvideVicoTheme(theme) {
        val blueLine = rememberLengthLine(CardBlue, 2.8.dp)
        val greenLine = rememberLengthLine(CardGreen, 2.4.dp)
        val layer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(blueLine, greenLine),
            pointSpacing = if (compact) 44.dp else 58.dp,
            rangeProvider = remember(range) {
                CartesianLayerRangeProvider.fixed(
                    minY = range.minY,
                    maxY = range.maxY
                )
            }
        )
        val marker = rememberChartMarker(
            formatter = remember(points) {
                DefaultCartesianMarker.ValueFormatter { _, targets ->
                    val target = targets.firstOrNull() as? LineCartesianLayerMarkerTarget
                    val x = target?.x?.roundToInt()
                    val point = x?.let(points::getOrNull)
                    if (point == null) {
                        ""
                    } else {
                        val values = target.points.map { it.entry.y }
                        val erect = values.getOrNull(0) ?: point.erect.toDouble()
                        val flaccid = values.getOrNull(1) ?: point.flaccid.toDouble()
                        formatLengthMarkerValue(erect, flaccid)
                    }
                }
            }
        )
        val bottomLabel = rememberTextComponent(
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            lineCount = 2
        )
        val chart = rememberCartesianChart(
            layer,
            startAxis = rememberStartAxis("cm", range.step),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = null,
                label = bottomLabel,
                tick = null,
                guideline = null,
                valueFormatter = axisFormatter,
                itemPlacer = remember {
                    HorizontalAxis.ItemPlacer.aligned(
                        addExtremeLabelPadding = true
                    )
                }
            ),
            marker = marker
        )
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(
                scrollEnabled = points.size > if (compact) 6 else 7,
                initialScroll = Scroll.Absolute.End
            ),
            zoomState = rememberVicoZoomState(zoomEnabled = false)
        )
    }
}

@Composable
private fun rememberLengthLine(color: Color, thickness: androidx.compose.ui.unit.Dp): LineCartesianLayer.Line {
    val point = LineCartesianLayer.Point(
        component = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(50),
            strokeFill = Fill(color),
            strokeThickness = 2.dp
        ),
        size = 7.dp
    )
    return LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness, StrokeCap.Round),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(Brush.verticalGradient(listOf(color.copy(alpha = 0.10f), Color.Transparent)))
        ),
        pointProvider = LineCartesianLayer.PointProvider.single(point),
        interpolator = LineCartesianLayer.Interpolator.cubic(0.18f)
    )
}
