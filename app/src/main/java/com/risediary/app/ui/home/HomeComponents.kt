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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.risediary.app.R
import com.risediary.app.ui.components.CompactRangeSwitcher
import com.risediary.app.ui.components.LiquidSegmentOption
import com.risediary.app.data.HomeCardOrderPolicy
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.risediary.app.ui.icons.AppIcons

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
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            trailing()
        } else if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                AppIcons.ChevronRight,
                null,
                Modifier.size(17.dp),
                tint = MiuixTheme.colorScheme.primary
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
            LiquidSegmentOption(volumeLabel, AppIcons.WaterDrop),
            LiquidSegmentOption(distanceLabel, AppIcons.TrackChanges)
        )
    }
    CompactRangeSwitcher(
        options = options,
        selectedIndex = if (isVolume) 0 else 1,
        onSelected = { if (it == 0) onSelectVolume() else onSelectDistance() },
        modifier = Modifier
            .width(176.dp)
            .height(38.dp),
        controlWidth = 144.dp
    )
}

internal fun parseCardOrder(orderJson: String, visibilityJson: String): List<String> =
    HomeCardOrderPolicy.visibleOrder(orderJson, visibilityJson, HomeCardOrderPolicy.currentIds)

internal fun resolveHomeCardOrder(savedOrder: List<String>, visibility: Map<String, Boolean>): List<String> =
    HomeCardOrderPolicy.normalizeIds(savedOrder, HomeCardOrderPolicy.currentIds)
        .filter { visibility[it] ?: true }