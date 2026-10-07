/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.risediary.app.R
import com.risediary.app.ui.onboarding.original.originalOnboardingSummary
import com.risediary.app.ui.onboarding.original.originalOnboardingText
import top.yukonga.miuix.kmp.basic.Text

/** Welcome is native. Completion uses the host transition, with no second animation clock. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun OnboardingHero(complete: Boolean, scrollState: ScrollState, animate: Boolean) {
    GuideHero(
        R.drawable.app_icon,
        stringResource(if (complete) R.string.oobe_complete_title else R.string.app_name),
        stringResource(if (complete) R.string.oobe_complete_subtitle else R.string.oobe_welcome_subtitle),
        scrollState, if (complete) 24.sp else 32.sp,
        if (complete) Color.White else originalOnboardingText(),
        if (complete) Color.White.copy(alpha = 0.7f) else originalOnboardingSummary(),
    )
}

@Composable
internal fun GuideHero(
    icon: Int?, title: String, subtitle: String?, scrollState: ScrollState,
    titleSize: TextUnit = 32.sp, titleColor: Color = Color.White,
    subtitleColor: Color = Color.White.copy(alpha = 0.7f),
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scrollState).heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Image(painterResource(icon), null, Modifier.size(90.dp).testTag("oobe_hero_icon"))
                Spacer(Modifier.height(30.dp))
            }
            Text(title, Modifier.fillMaxWidth(), fontSize = titleSize,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, Modifier.fillMaxWidth(), fontSize = 14.sp,
                    textAlign = TextAlign.Center, color = subtitleColor)
            }
        }
    }
}
