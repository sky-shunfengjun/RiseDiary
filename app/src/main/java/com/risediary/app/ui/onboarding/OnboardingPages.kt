/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.original.OriginalOnboardingAccent
import com.risediary.app.ui.onboarding.original.originalOnboardingSummary
import com.risediary.app.ui.onboarding.original.originalOnboardingText
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** HyperCeiler's preview / centered title / body proportions, within a scrollable viewport. */
@Composable
internal fun OnboardingPage(
    title: String,
    subtitle: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Info,
    showIcon: Boolean = true,
    compactLayout: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 528.dp).fillMaxWidth()
                .verticalScroll(scrollState).heightIn(min = maxHeight)
                .padding(LocalGuideContentPadding.current)
                .padding(horizontal = 24.dp, vertical = if (compactLayout) 16.dp else 24.dp)
                .testTag("oobe_original_body"),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = if (compactLayout) 8.dp else 12.dp,
                    bottom = if (compactLayout) 20.dp else 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showIcon) {
                    Icon(icon, null, Modifier.size(if (compactLayout) 56.dp else 70.dp).testTag("oobe_step_icon"), tint = OriginalOnboardingAccent)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    title, Modifier.fillMaxWidth().testTag("oobe_step_title"),
                    fontSize = 32.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    color = originalOnboardingText(),
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        subtitle, Modifier.fillMaxWidth(), fontSize = 14.sp,
                        textAlign = TextAlign.Center, color = originalOnboardingSummary(),
                    )
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            Spacer(Modifier.height(if (compactLayout) 16.dp else 24.dp))
        }
    }
}

/** Normal setting surfaces follow the same miuix controls as the app. */
@Composable
internal fun OnboardingCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, content = content)
}

/** Page actions use miuix; navigation chrome alone uses the existing glass components. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun OnboardingAction(
    text: String,
    icon: ImageVector,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centered: Boolean = false,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentClick by rememberUpdatedState(onClick)
    Button(
        onClick = { if (currentEnabled) currentClick() },
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        enabled = enabled,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
        Spacer(Modifier.width(12.dp))
        Text(
            text, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
internal fun OnboardingStatus(icon: ImageVector, title: String, summary: String) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = OriginalOnboardingAccent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = originalOnboardingText())
            Text(summary, fontSize = 14.sp, color = originalOnboardingSummary())
        }
    }
}
