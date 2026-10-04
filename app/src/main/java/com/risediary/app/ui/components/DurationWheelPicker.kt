package com.risediary.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.risediary.app.R
import com.risediary.app.util.DurationParts
import com.risediary.app.util.DurationPolicy
import com.risediary.app.util.durationPartsFromSeconds
import com.risediary.app.util.durationPartsWithHours
import com.risediary.app.util.durationSecondsFromParts
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Controlled hour/minute/second selector; the final hour has no extra minutes or seconds. */
@Composable
fun DurationWheelPicker(
    parts: DurationParts,
    onValueChange: (DurationParts) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxHours = DurationPolicy.MAX_SECONDS / 3_600
    val atLimit = parts.hours == maxHours
    val hourFormat = stringResource(R.string.duration_picker_hour_format)
    val minuteFormat = stringResource(R.string.duration_picker_minute_format)
    val secondFormat = stringResource(R.string.duration_picker_second_format)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberPicker(
            value = parts.hours,
            onValueChange = { onValueChange(durationPartsWithHours(parts, it)) },
            range = 0..maxHours,
            label = { hourFormat.format(it) },
            visibleItemCount = 3,
            wrapAround = false,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        NumberPicker(
            value = if (atLimit) 0 else parts.minutes,
            onValueChange = { onValueChange(parts.copy(minutes = if (atLimit) 0 else it)) },
            range = if (atLimit) 0..0 else 0..59,
            label = { minuteFormat.format(it) },
            visibleItemCount = 3,
            wrapAround = false,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
        NumberPicker(
            value = if (atLimit) 0 else parts.seconds,
            onValueChange = { onValueChange(parts.copy(seconds = if (atLimit) 0 else it)) },
            range = if (atLimit) 0..0 else 0..59,
            label = { secondFormat.format(it) },
            visibleItemCount = 3,
            wrapAround = false,
            textStyle = MiuixTheme.textStyles.title2,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DurationPickerBottomSheet(
    totalSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = durationPartsFromSeconds(totalSeconds)
    var parts by remember(initial) { mutableStateOf(initial) }
    LiquidDialog(
        onDismissRequest = onDismiss,
        alignment = Alignment.Center,
        shape = ContinuousRoundedRectangle(40.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier)
        ) {
            Text(
                text = stringResource(R.string.duration_picker_title),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.duration_picker_hint),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(12.dp))
            DurationWheelPicker(
                parts = parts,
                onValueChange = { parts = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.045f))
                )
                Button(
                    onClick = { onConfirm(durationSecondsFromParts(parts)) },
                    enabled = durationSecondsFromParts(parts) > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    cornerRadius = 24.dp
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        }
    }
}

/**
 * Single-column scroll wheel that snaps to items. The item nearest the viewport centre
 * is reported as selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    label: String,
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    padToTwoDigits: Boolean = true
) {
    val itemHeightDp = 44.dp
    val density = LocalDensity.current
    val count = range.last - range.first + 1
    val selectedInput = value.coerceIn(range)
    val state = key(range.first, range.last) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = selectedInput - range.first
        )
    }

    // Derive selected item from the viewport centre
    val selectedValue by remember(state, range.first, range.last) {
        derivedStateOf {
            centeredValue(state, range, selectedInput)
        }
    }

    val latestSelectedInput by rememberUpdatedState(selectedInput)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    // Only report the selection once the user stops dragging/flinging. Updating the
    // parent on every frame while scrolling made the wheel janky and caused the
    // LaunchedEffect(selectedInput) to fight the gesture. Read the current list
    // position inside the coroutine: capturing selectedValue here would freeze the
    // initial value for the lifetime of this effect.
    LaunchedEffect(state, range.first, range.last) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    // Let a snap animation that starts on the pointer-up frame take
                    // ownership before reading the final centred item.
                    withFrameNanos { }
                    if (!state.isScrollInProgress) {
                        val settled = centeredValue(state, range, latestSelectedInput)
                        if (settled != latestSelectedInput) {
                            latestOnValueChange(settled)
                        }
                    }
                }
            }
    }

    // Pre-compute the two-item-height padding in px
    val padPx = with(density) { (itemHeightDp * 2f) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Column label
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeightDp * 5)
                .clip(RoundedCornerShape(20.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.025f)),
            contentAlignment = Alignment.Center
        ) {
            // A compact selected capsule replaces the old full-width flat stripe.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(itemHeightDp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(18.dp)
                    )
            )

            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = padPx),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
            ) {
                items(count) { index ->
                    val value = range.first + index
                    val isSelected = value == selectedValue

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeightDp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (padToTwoDigits) "%02d".format(value) else "$value",
                            fontSize = if (isSelected) 22.sp else 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun centeredValue(
    state: LazyListState,
    range: IntRange,
    fallback: Int
): Int {
    val info = state.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return fallback
    val centreY = info.viewportStartOffset + info.viewportSize.height / 2
    return info.visibleItemsInfo
        .minByOrNull { abs((it.offset + it.size / 2) - centreY) }
        ?.index
        ?.let { range.first + it }
        ?.coerceIn(range)
        ?: fallback
}
