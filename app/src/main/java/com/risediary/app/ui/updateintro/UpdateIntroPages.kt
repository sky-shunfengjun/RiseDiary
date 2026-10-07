/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.updateintro

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.*
import com.risediary.app.ui.onboarding.original.originalOnboardingSummary
import com.risediary.app.ui.onboarding.original.originalOnboardingText
import com.risediary.app.ui.settings.SettingsToggleItem
import top.yukonga.miuix.kmp.basic.*

/** The screenshot stays above the title; only live settings below it accept input. */
@Composable
private fun IntroFeaturePage(
    title: String, subtitle: String, scroll: ScrollState,
    preview: @Composable (Dp) -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    val padding = LocalGuideContentPadding.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bodyHeight = (maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()).coerceAtLeast(0.dp)
        val previewHeight = (bodyHeight * 0.34f).coerceIn(136.dp, 200.dp)
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 480.dp).fillMaxWidth()
            .verticalScroll(scroll).heightIn(min = maxHeight)
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp).testTag("update_intro_feature_body"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            preview(previewHeight)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.fillMaxWidth().testTag("oobe_step_title"), fontSize = 32.sp, lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = originalOnboardingText())
                Text(subtitle, Modifier.fillMaxWidth(), fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, color = originalOnboardingSummary())
            }
            content()
        }
    }
}

/** Original user screenshots: no crop, distortion, synthetic controls or playback actions. */
@Composable
private fun IntroScreenshot(@DrawableRes resource: Int, description: String, maxHeight: Dp) {
    val painter = painterResource(resource)
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight * ratio)
        Image(painter = painter, contentDescription = description, contentScale = ContentScale.Fit,
            modifier = Modifier.width(width).aspectRatio(ratio).clip(RoundedCornerShape(20.dp))
                .testTag("update_intro_preview"))
    }
}

@Composable
private fun IntroNote(text: String) {
    Text(text, Modifier.fillMaxWidth(), fontSize = 13.sp, textAlign = TextAlign.Center,
        color = originalOnboardingSummary())
}

@Composable
internal fun RecordingTimerIntroPage(
    state: UpdateIntroUiState, scroll: ScrollState, enabled: Boolean, onMaximum: (Int) -> Unit,
    status: @Composable () -> Unit = {},
) {
    IntroFeaturePage(stringResource(R.string.update_intro_recording_title),
        stringResource(R.string.update_intro_recording_subtitle), scroll, preview = { height ->
            IntroScreenshot(R.drawable.update_intro_quantity, stringResource(R.string.update_intro_recording_demo), height)
        }) {
        if (state.ready) Card(Modifier.fillMaxWidth()) {
            GuidePredictionMaximumControl(state.predictionMaxTicks, enabled, onMaximum,
                title = stringResource(R.string.settings_prediction_max))
        }
        IntroNote(stringResource(R.string.update_intro_legacy_note))
        status()
    }
}

@Composable
internal fun NotificationsIntroPage(
    state: UpdateIntroUiState, scroll: ScrollState, backdrop: Backdrop, enabled: Boolean,
    notificationsAllowed: Boolean, onPermission: () -> Unit, onLiveUpdates: (Boolean) -> Unit,
    status: @Composable () -> Unit = {},
) {
    IntroFeaturePage(stringResource(R.string.update_intro_notifications_page_title),
        stringResource(R.string.update_intro_notifications_page_subtitle), scroll, preview = { height ->
            IntroScreenshot(R.drawable.update_intro_notifications, stringResource(R.string.update_intro_notification_demo), minOf(height, 160.dp))
        }) {
        if (state.ready) Card(Modifier.fillMaxWidth()) {
            SettingsToggleItem(AppIcons.Schedule, stringResource(R.string.settings_live_updates),
                stringResource(R.string.update_intro_live_summary), state.liveUpdatesEnabled,
                { if (enabled) onLiveUpdates(it) }, enabled)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntroNote(stringResource(if (notificationsAllowed) R.string.oobe_notifications_on else R.string.oobe_notifications_off))
            if (!notificationsAllowed) OnboardingAction(stringResource(R.string.oobe_enable_notifications),
                AppIcons.NotificationsActive, backdrop, onPermission, enabled = enabled)
        }
        status()
    }
}

@Composable
internal fun VideoPrivacyIntroPage(
    state: UpdateIntroUiState, scroll: ScrollState, enabled: Boolean, onVideoHidden: (Boolean) -> Unit,
    status: @Composable () -> Unit = {},
) {
    IntroFeaturePage(stringResource(R.string.update_intro_video_title),
        stringResource(R.string.update_intro_video_subtitle), scroll, preview = { height ->
            IntroScreenshot(R.drawable.update_intro_video, stringResource(R.string.update_intro_player_demo), height)
        }) {
        if (state.ready) Card(Modifier.fillMaxWidth()) {
            SettingsToggleItem(AppIcons.VisibilityOff, stringResource(R.string.settings_detail_video_hidden),
                stringResource(R.string.update_intro_hidden_summary), state.detailVideoHidden,
                { if (enabled) onVideoHidden(it) }, enabled)
        }
        status()
    }
}
