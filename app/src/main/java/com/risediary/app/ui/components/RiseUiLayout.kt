/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

object RiseUiSpacing {
    val Page = 20.dp
    val Group = 14.dp
    val Content = 16.dp
    val Icon = 36.dp
    val IconGap = 12.dp
}

/** Use for an actual group, not as an obligatory wrapper around every page. */
@Composable
fun RisePreferenceGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = RiseUiSpacing.Content),
            style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** A null icon reserves the same column when its group contains other leading icons. */
@Composable
fun RiseContentIcon(icon: ImageVector?, modifier: Modifier = Modifier) {
    Box(modifier.size(RiseUiSpacing.Icon)
        .then(if (icon == null) Modifier else Modifier.clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = .11f))),
        contentAlignment = Alignment.Center) {
        icon?.let { Icon(it, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary) }
    }
}

/** Native preference layout; reserveIconSpace must be consistent within the group. */
@Composable
fun RisePreferenceLink(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    reserveIconSpace: Boolean = icon != null,
    enabled: Boolean = true
) {
    ArrowPreference(title = title, summary = summary, onClick = onClick, enabled = enabled,
        modifier = modifier,
        startAction = if (reserveIconSpace || icon != null) ({
            RiseContentIcon(icon, Modifier.padding(end = RiseUiSpacing.IconGap))
        }) else null)
}

/** Object names are single-line; the complete name remains available to accessibility. */
@Composable
fun RiseListItem(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    reserveIconSpace: Boolean = icon != null,
    trailing: @Composable () -> Unit = {}
) {
    // Card is not imposed here: a caller may place these rows in one group or directly on a page.
    androidx.compose.foundation.layout.Box(modifier) {
        Row(Modifier.fillMaxWidth().then(Modifier.semantics(mergeDescendants = true) { contentDescription = name })
            .clickable(onClick = onClick).padding(RiseUiSpacing.Content),
            verticalAlignment = Alignment.CenterVertically) {
            if (reserveIconSpace || icon != null) {
                RiseContentIcon(icon)
                Spacer(Modifier.width(RiseUiSpacing.IconGap))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
                summary?.let { Text(it, style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            }
            Box(Modifier.padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

@Composable
fun RiseLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
        TextField(value = value, onValueChange = onValueChange, enabled = enabled,
            singleLine = singleLine, keyboardOptions = keyboardOptions,
            label = placeholder, useLabelAsPlaceholder = true, minLines = minLines, maxLines = maxLines,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
        RiseFieldError(error)
    }
}

@Composable
fun RiseFieldError(message: String?, modifier: Modifier = Modifier) {
    if (!message.isNullOrBlank()) Text(message, modifier,
        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
}

@Composable
fun RiseEmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        Text(message, style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun RiseMetric(value: String, unit: String, label: String, modifier: Modifier = Modifier,
    valueStyle: TextStyle = MiuixTheme.textStyles.title3.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, Modifier.alignByBaseline(), style = valueStyle, color = MiuixTheme.colorScheme.primary)
            Text(unit, Modifier.alignByBaseline(), style = MiuixTheme.textStyles.body1)
        }
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
