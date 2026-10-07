/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidSlider
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.original.originalOnboardingSurface
import com.risediary.app.ui.onboarding.original.originalOnboardingSummary
import com.risediary.app.ui.onboarding.original.originalOnboardingText
import com.risediary.app.ui.policy.PolicyDocument
import com.risediary.app.util.PredictionQuantitySettings
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.RadioButton

@Composable
internal fun StatementOnboardingPage(
    accepted: Boolean, enabled: Boolean, scrollState: ScrollState, backdrop: Backdrop,
    onAccepted: (Boolean) -> Unit, onRead: (PolicyDocument) -> Unit,
    compact: Boolean = false,
) {
    OnboardingPage(stringResource(R.string.oobe_statement_title), "", scrollState, icon = AppIcons.Notes, compactLayout = compact) {
        OnboardingCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.oobe_statement_subtitle), Modifier.padding(if (compact) 16.dp else 20.dp),
                fontSize = if (compact) 14.sp else 16.sp, color = originalOnboardingSummary(),
            )
        }
        if (compact) {
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 300.dp && fontScale <= 1.15f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OnboardingAction(stringResource(R.string.policy_terms_title), AppIcons.Notes, backdrop,
                            { if (enabled) onRead(PolicyDocument.TERMS) }, Modifier.weight(1f), enabled, centered = true)
                        OnboardingAction(stringResource(R.string.policy_privacy_title), AppIcons.Lock, backdrop,
                            { if (enabled) onRead(PolicyDocument.PRIVACY) }, Modifier.weight(1f), enabled, centered = true)
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OnboardingAction(stringResource(R.string.policy_terms_title), AppIcons.Notes, backdrop,
                        { if (enabled) onRead(PolicyDocument.TERMS) }, enabled = enabled, centered = true)
                    OnboardingAction(stringResource(R.string.policy_privacy_title), AppIcons.Lock, backdrop,
                        { if (enabled) onRead(PolicyDocument.PRIVACY) }, enabled = enabled, centered = true)
                }
            }
        } else {
            OnboardingAction(stringResource(R.string.policy_terms_title), AppIcons.Notes, backdrop,
                { if (enabled) onRead(PolicyDocument.TERMS) }, enabled = enabled, centered = true)
            OnboardingAction(stringResource(R.string.policy_privacy_title), AppIcons.Lock, backdrop,
                { if (enabled) onRead(PolicyDocument.PRIVACY) }, enabled = enabled, centered = true)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("oobe_agreement")
                .toggleable(accepted, enabled = enabled, role = Role.Checkbox,
                    onValueChange = { if (enabled) onAccepted(it) }).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                state = if (accepted) ToggleableState.On else ToggleableState.Off,
                onClick = { if (enabled) onAccepted(!accepted) }, enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                stringResource(R.string.oobe_statement_accept), Modifier.weight(1f),
                fontSize = 14.sp, color = originalOnboardingSummary(),
            )
        }
    }
}

@Composable
internal fun ProfileOnboardingPage(
    username: String, enabled: Boolean, scrollState: ScrollState, onUsernameChange: (String) -> Unit,
) {
    OnboardingPage(stringResource(R.string.oobe_profile_title), stringResource(R.string.oobe_profile_subtitle), scrollState, icon = AppIcons.Person) {
        TextField(
            value = username, onValueChange = { if (enabled) onUsernameChange(it) },
            modifier = Modifier.fillMaxWidth().testTag("oobe_username"), singleLine = true,
            enabled = enabled, label = stringResource(R.string.settings_username), useLabelAsPlaceholder = false,
        )
    }
}

@Composable
internal fun ThemeOnboardingPage(
    themeMode: String, scrollState: ScrollState, onThemeSelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    val options = listOf(
        "system" to stringResource(R.string.settings_theme_system),
        "light" to stringResource(R.string.settings_theme_light),
        "dark" to stringResource(R.string.settings_theme_dark),
    )
    val currentEnabled by rememberUpdatedState(enabled)
    val currentSelection by rememberUpdatedState(onThemeSelected)
    OnboardingPage(stringResource(R.string.oobe_theme_title), stringResource(R.string.oobe_theme_subtitle), scrollState, icon = AppIcons.Palette) {
        options.forEach { (key, label) ->
            OnboardingCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("oobe_theme_" + key)
                        .selectable(
                            selected = themeMode == key, enabled = enabled, role = Role.RadioButton,
                            onClick = { if (currentEnabled) currentSelection(key) },
                        ).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(label, Modifier.weight(1f), fontSize = 18.sp, color = originalOnboardingText())
                    RadioButton(themeMode == key, { if (currentEnabled) currentSelection(key) },
                        modifier = Modifier.clearAndSetSemantics {}, enabled = enabled)
                }
            }
        }
    }
}

/** The glass slider samples only its static card content. */
@Composable
internal fun RecordingOnboardingPage(
    predictionMaxTicks: Int?, scrollState: ScrollState, onPredictionMaximumChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    OnboardingPage(stringResource(R.string.oobe_prediction_title), stringResource(R.string.oobe_prediction_subtitle), scrollState, icon = AppIcons.WaterDrop) {
        if (predictionMaxTicks != null) OnboardingCard(Modifier.fillMaxWidth()) {
            GuidePredictionMaximumControl(predictionMaxTicks, enabled, onPredictionMaximumChange)
        }
    }
}

@Composable
internal fun GuidePredictionMaximumControl(
    predictionMaxTicks: Int, enabled: Boolean, onPredictionMaximumChange: (Int) -> Unit,
    title: String? = null,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChange by rememberUpdatedState(onPredictionMaximumChange)
    var previewTicks by remember { mutableIntStateOf(predictionMaxTicks) }
    var adjusting by remember { mutableStateOf(false) }
    LaunchedEffect(predictionMaxTicks) { if (!adjusting) previewTicks = predictionMaxTicks }
    LaunchedEffect(enabled) { if (!enabled) adjusting = false }
    val surface by rememberUpdatedState(originalOnboardingSurface())
    val localBackdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().layerBackdrop(localBackdrop)
            .padding(horizontal = 20.dp, vertical = if (title != null) 12.dp else 20.dp)) {
            if (title != null) FlowRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = originalOnboardingText())
                Text(stringResource(R.string.settings_prediction_summary, PredictionQuantitySettings.formatTicks(previewTicks)),
                    fontSize = 18.sp, fontWeight = FontWeight.Medium, color = originalOnboardingText())
            } else Text(stringResource(R.string.settings_prediction_summary, PredictionQuantitySettings.formatTicks(previewTicks)),
                fontSize = 18.sp, fontWeight = FontWeight.Medium, color = originalOnboardingText())
            Spacer(Modifier.height(if (title != null) 54.dp else 60.dp))
        }
        LiquidSlider(
            value = { previewTicks / 10f },
            onValueChange = {
                if (currentEnabled) {
                    adjusting = true
                    previewTicks = (it * 10).roundToInt().coerceIn(1, PredictionQuantitySettings.MAX_SETTING_TICKS)
                }
            },
            valueRange = 0.1f..15f, steps = 148, backdrop = localBackdrop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 10.dp)
                .testTag("oobe_prediction_slider"),
            onValueChangeFinished = { adjusting = false; if (currentEnabled) currentChange(previewTicks) },
            enabled = enabled,
        )
    }
}
