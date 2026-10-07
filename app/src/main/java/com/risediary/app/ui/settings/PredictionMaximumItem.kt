package com.risediary.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.components.AnimatedUiVisibility
import com.risediary.app.ui.components.InlineStatusContent
import com.risediary.app.util.PredictionQuantitySettings
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PredictionMaximumItem(
    value: Int?,
    onValueChange: (Int) -> Unit,
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    var previousError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(error) { error?.let { previousError = it } }
    Column(Modifier.fillMaxWidth()) {
        if (value == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIcon(AppIcons.WaterDrop)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_prediction_max), style = MiuixTheme.textStyles.headline1)
                    if (error == null) Text(
                        stringResource(R.string.settings_prediction_loading),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            var previewTicks by remember { mutableIntStateOf(value) }
            var adjusting by remember { mutableStateOf(false) }
            LaunchedEffect(value) {
                if (!adjusting) previewTicks = value
            }
            SettingsSliderItem(
                icon = AppIcons.WaterDrop,
                title = stringResource(R.string.settings_prediction_max),
                subtitle = stringResource(R.string.settings_prediction_summary, PredictionQuantitySettings.formatTicks(previewTicks)),
                value = previewTicks / 10f,
                valueRange = 0.1f..15f,
                steps = 148,
                onValueChange = {
                    adjusting = true
                    previewTicks = (it * 10).roundToInt().coerceIn(1, PredictionQuantitySettings.MAX_SETTING_TICKS)
                },
                onValueChangeFinished = {
                    adjusting = false
                    onValueChange(previewTicks)
                }
            )
        }
        AnimatedUiVisibility(error != null) { active ->
            InlineStatusContent(
                messages = listOfNotNull(error ?: previousError),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                onRetry = onRetry,
                enabled = active
            )
        }
    }
}
