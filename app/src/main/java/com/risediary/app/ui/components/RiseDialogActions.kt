/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One renderer for alerts, pickers and sheet footers. No whole-button color filters. */
@Composable
fun RiseDialogActionButton(
    action: RiseDialogAction,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val palette = dialogActionPalette(
        LocalRiseDarkTheme.current, emphasized, action.tone == DialogActionTone.Destructive,
        action.enabled, MiuixTheme.colorScheme.error.toArgb().toLong() and 0xFFFFFFFF,
        primaryColor = MiuixTheme.colorScheme.primary.toArgb().toLong() and 0xFFFFFFFF
    )
    val background = Color(palette.background)
    val foreground = Color(palette.foreground)
    TextButton(
        text = action.text,
        onClick = action::perform,
        enabled = action.enabled,
        cornerRadius = 1000.dp,
        modifier = modifier.heightIn(min = uiActionHeightDp(LocalDensity.current.fontScale).dp).clip(CircleShape),
        colors = ButtonDefaults.textButtonColors(
            color = background, disabledColor = background,
            textColor = foreground, disabledTextColor = foreground
        )
    )
}

@Composable
fun RiseDialogActions(
    confirmAction: RiseDialogAction,
    modifier: Modifier = Modifier,
    dismissAction: RiseDialogAction? = null,
    neutralAction: RiseDialogAction? = null,
    adaptiveActions: Boolean = true
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = adaptiveActions && dialogActionsShouldStack(maxWidth.value, fontScale)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            neutralAction?.let {
                RiseDialogActionButton(it, Modifier.fillMaxWidth().testTag("rise-dialog-neutral"))
            }
            if (stacked) {
                dismissAction?.let { RiseDialogActionButton(it, Modifier.fillMaxWidth().testTag("rise-dialog-dismiss")) }
                RiseDialogActionButton(confirmAction, Modifier.fillMaxWidth().testTag("rise-dialog-confirm"), emphasized = true)
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    dismissAction?.let { RiseDialogActionButton(it, Modifier.weight(1f).fillMaxHeight().testTag("rise-dialog-dismiss")) }
                    RiseDialogActionButton(confirmAction, Modifier.weight(1f).fillMaxHeight().testTag("rise-dialog-confirm"), emphasized = true)
                }
            }
        }
    }
}
