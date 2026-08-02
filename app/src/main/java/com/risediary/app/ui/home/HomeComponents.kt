package com.risediary.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.085f))
            .padding(horizontal = 6.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(140)))
                    .togetherWith(
                        slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(100))
                    )
            },
            label = "home_metric_value"
        ) { animatedValue ->
            Text(animatedValue, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
internal fun HomeCardHeader(
    icon: ImageVector,
    title: String,
    actionLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            trailing()
        } else if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                Icons.Default.ChevronRight,
                null,
                Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun TrendSelector(
    isVolume: Boolean,
    onSelectVolume: () -> Unit,
    onSelectDistance: () -> Unit
) {
    val volumeLabel = stringResource(R.string.home_volume)
    val distanceLabel = stringResource(R.string.home_distance)
    val options = remember(volumeLabel, distanceLabel) {
        listOf(
            LiquidSegmentOption(volumeLabel, Icons.Default.WaterDrop),
            LiquidSegmentOption(distanceLabel, Icons.Default.TrackChanges)
        )
    }
    val currentTrackColor by rememberUpdatedState(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f)
    )
    val backdrop = rememberLayerBackdrop { drawContent() }
    Box(modifier = Modifier.width(176.dp).height(38.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .requiredHeight(70.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(144.dp)
                        .height(38.dp)
                        .clip(ContinuousCapsule)
                        .background(currentTrackColor)
                )
            }
            LiquidSegmentedControl(
                options = options,
                selectedIndex = if (isVolume) 0 else 1,
                onSelected = { if (it == 0) onSelectVolume() else onSelectDistance() },
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.CenterEnd).width(144.dp),
                containerHeight = 38.dp,
                contentPadding = 3.dp,
                showIcons = false,
                labelFontSize = 12.sp,
                showSelectionShadow = false
            )
        }
    }
}

internal fun parseCardOrder(orderJson: String, visibilityJson: String): List<String> {
    val order = try {
        val array = JSONArray(orderJson)
        (0 until array.length()).map { array.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
    val visibility = try {
        val value = JSONObject(visibilityJson)
        value.keys().asSequence().associate { it to value.getBoolean(it) }
    } catch (_: Exception) {
        emptyMap()
    }
    return resolveHomeCardOrder(order, visibility)
}

internal fun resolveHomeCardOrder(
    savedOrder: List<String>,
    visibility: Map<String, Boolean>
): List<String> {
    val defaultOrder = listOf("checkin", "overview", "trend", "length", "achievement")
    val legacyMap = mapOf(
        "recent7" to "checkin",
        "summary" to "checkin",
        "heatmap" to "checkin",
        "distance" to "trend",
    )
    val normalized = (if (savedOrder.isEmpty()) defaultOrder else savedOrder)
        .map { legacyMap[it] ?: it }
        .let { ids ->
            val seen = mutableSetOf<String>()
            ids.filter { seen.add(it) }
        }
    val ordered = normalized + defaultOrder.filterNot(normalized::contains)
    return ordered.filter { visibility[it] ?: true }
}
