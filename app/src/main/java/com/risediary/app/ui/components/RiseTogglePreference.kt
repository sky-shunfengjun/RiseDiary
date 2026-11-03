/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Existing settings glass recipe: capture text and background, never capture the toggle itself. */
@Composable
fun RiseTogglePreference(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    reserveIconSpace: Boolean = true,
    enabled: Boolean = true
) {
    val surface by rememberUpdatedState(MiuixTheme.colorScheme.surfaceContainer)
    val backdrop = rememberLayerBackdrop { drawRect(surface); drawContent() }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().layerBackdrop(backdrop)
            .padding(start = 16.dp, end = 92.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (reserveIconSpace || icon != null) {
                RiseContentIcon(icon)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        LiquidToggle(checked, onCheckedChange, backdrop,
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).semantics { contentDescription = title }, enabled)
    }
}
