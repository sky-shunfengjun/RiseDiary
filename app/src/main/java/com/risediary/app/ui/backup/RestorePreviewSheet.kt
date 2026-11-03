/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.backup

import com.risediary.app.ui.components.RiseDialogAction
import com.risediary.app.ui.components.DialogActionTone
import com.risediary.app.ui.components.RiseDialogActions
import com.risediary.app.ui.components.RiseDialogActionButton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.data.backup.*
import com.risediary.app.ui.components.copyErrorOnLongPress
import com.risediary.app.ui.components.dialogActionsShouldStack
import com.risediary.app.ui.components.uiActionHeightDp
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private data class RestoreSheetContent(val preview: RestorePreview, val busy: Boolean, val message: String)

/** Keep one native sheet mounted until its exit animation finishes, even after the preview is cleared. */
@Composable
internal fun RestorePreviewSheet(
    preview: RestorePreview?,
    busy: Boolean,
    message: String,
    changeMode: (RestoreMode) -> Unit,
    confirm: () -> Unit,
    cancel: () -> Unit
) {
    val incoming = preview?.let { RestoreSheetContent(it, busy, message) }
    var retained by remember { mutableStateOf<RestoreSheetContent?>(null) }
    SideEffect { incoming?.let { retained = it } }
    val shown = incoming ?: retained ?: return
    val visible = preview != null
    val interactive = visible && !busy
    val height = LocalWindowInfo.current.containerDpSize.height * .85f
    val dismiss = { if (interactive) cancel() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        OverlayBottomSheet(
            show = visible,
            title = stringResource(R.string.restore_preview_title),
            startAction = {
                IconButton(onClick = dismiss, enabled = interactive,
                    modifier = Modifier.padding(start = 12.dp).size(48.dp)) {
                    Icon(AppIcons.Close, stringResource(R.string.restore_preview_close), Modifier.size(24.dp))
                }
            },
            modifier = Modifier.height(height).testTag("restore_preview_sheet")
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                .copyErrorOnLongPress(shown.message, interactive && !shown.message.startsWith("手机数据已变化")),
            insideMargin = DpSize(0.dp, 0.dp),
            allowDismiss = interactive,
            renderInRootScaffold = false,
            onDismissRequest = dismiss,
            onDismissFinished = { if (preview == null) retained = null }
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    // Old counts and controls leave immediately; calculation uses the same remaining area.
                    if (shown.busy) {
                        val calculating = stringResource(R.string.restore_preview_processing)
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp).semantics { contentDescription = calculating },
                            strokeWidth = 3.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = MiuixTheme.colorScheme.primary)
                        )
                    } else {
                        RestorePreviewBody(shown.preview, shown.message, interactive, changeMode)
                    }
                }
                RestoreSheetActions(shown.preview.mode, interactive, confirm, dismiss)
            }
        }
    }
}

@Composable
private fun RestorePreviewBody(preview: RestorePreview, message: String, enabled: Boolean, changeMode: (RestoreMode) -> Unit) {
    AnimatedVisibility(
        visibleState = remember(preview.preparationId, preview.revision) {
            MutableTransitionState(false).apply { targetState = true }
        },
        enter = fadeIn(tween(170))
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RestoreModeCards(preview.mode, enabled, changeMode)
            Text(
                stringResource(if (preview.mode == RestoreMode.MERGE) R.string.restore_merge_explanation else R.string.restore_replace_explanation),
                style = MiuixTheme.textStyles.body2,
                color = if (preview.mode == RestoreMode.REPLACE) MiuixTheme.colorScheme.error
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            RestoreRecordSummary(stringResource(R.string.restore_flights), preview.flights, preview.mode, isFlight = true)
            RestoreRecordSummary(stringResource(R.string.restore_lengths), preview.lengths, preview.mode, isFlight = false)
            if (preview.mode == RestoreMode.MERGE && listOf(preview.flights, preview.lengths).any {
                    it.backup > it.added + it.updated + it.skipped
                }) {
                Text(stringResource(R.string.restore_legacy_duplicates), style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            RestoreOtherData()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_settings_changes), style = MiuixTheme.textStyles.title4)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (preview.settingsChanges.isEmpty()) Text(stringResource(R.string.restore_settings_unchanged),
                            style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        else preview.settingsChanges.forEachIndexed { index, change ->
                            if (index > 0) HorizontalDivider()
                            Text(change, style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
            }
            if (message.isNotBlank()) Text(message,
                color = if (message.startsWith("手机数据已变化")) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RestoreSheetActions(mode: RestoreMode, enabled: Boolean, confirm: () -> Unit, cancel: () -> Unit) {
    RiseDialogActions(
        confirmAction = RiseDialogAction(stringResource(R.string.restore_confirm), confirm,
            if (mode == RestoreMode.REPLACE) DialogActionTone.Destructive else DialogActionTone.Normal, enabled),
        dismissAction = RiseDialogAction(stringResource(R.string.restore_cancel), cancel, DialogActionTone.Normal, enabled),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
private fun RestoreModeCards(currentMode: RestoreMode, enabled: Boolean, changeMode: (RestoreMode) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RestoreMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            val foreground = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
            Card(
                modifier = Modifier.weight(1f).semantics { if (!enabled) disabled() },
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                onClick = { if (enabled && !isSelected) changeMode(mode) },
                colors = CardDefaults.defaultColors(color = if (isSelected) Color(com.risediary.app.ui.components.RiseNormalActionColor) else MiuixTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().heightIn(min = 80.dp).semantics {
                    selected = isSelected
                    role = Role.RadioButton
                }.padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (mode == RestoreMode.MERGE) AppIcons.Add else AppIcons.Refresh, null, Modifier.size(24.dp), tint = foreground)
                    Text(stringResource(if (mode == RestoreMode.MERGE) R.string.restore_merge else R.string.restore_replace),
                        fontWeight = FontWeight.SemiBold, color = foreground)
                    Text(stringResource(if (mode == RestoreMode.MERGE) R.string.restore_merge_hint else R.string.restore_replace_hint),
                        style = MiuixTheme.textStyles.body2, color = foreground.copy(alpha = if (isSelected) .95f else .85f))
                }
            }
        }
    }
}

@Composable
private fun RestoreRecordSummary(name: String, count: RestoreCounts, mode: RestoreMode, isFlight: Boolean) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (isFlight) AppIcons.Notes else AppIcons.Straighten, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
                Text(name, style = MiuixTheme.textStyles.title4)
            }
            if (mode == RestoreMode.MERGE) {
                Text(stringResource(R.string.restore_final_count, count.final), style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.restore_added_count, count.added), style = MiuixTheme.textStyles.body2)
                    Text(stringResource(R.string.restore_updated_count, count.updated), style = MiuixTheme.textStyles.body2)
                    Text(stringResource(R.string.restore_skipped_count, count.skipped), style = MiuixTheme.textStyles.body2)
                }
            } else {
                Text(stringResource(R.string.restore_backup_count, count.backup), style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary)
                Text(stringResource(R.string.restore_removed_count, count.current), style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RestoreOtherData() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.restore_other_data), style = MiuixTheme.textStyles.title4)
            Text(stringResource(R.string.restore_labels_achievements), style = MiuixTheme.textStyles.body2)
            Text(stringResource(R.string.restore_settings_rule), style = MiuixTheme.textStyles.body2)
            Text(stringResource(R.string.restore_local_protection), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
