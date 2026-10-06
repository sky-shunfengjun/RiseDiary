/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.policy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Local text, one native sheet. Retained content remains stable until native exit finishes. */
@Composable
fun PolicySheet(document: PolicyDocument?, onDismiss: () -> Unit) {
    var retained by remember { mutableStateOf(document) }
    SideEffect { document?.let { retained = it } }
    val shownDocument = document ?: retained ?: return
    val height = LocalWindowInfo.current.containerDpSize.height * 0.8f
    val uriHandler = LocalUriHandler.current
    val projectUrl = stringResource(R.string.about_github_url)
    var linkError by remember { mutableStateOf(false) }
    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
        OverlayBottomSheet(
            show = document != null,
            title = stringResource(shownDocument.title),
            startAction = {
                IconButton(onClick = onDismiss, enabled = document != null, modifier = Modifier.padding(start = 12.dp).size(48.dp)) {
                    Icon(AppIcons.Close, stringResource(R.string.policy_close), Modifier.size(24.dp))
                }
            },
            modifier = Modifier.height(height).testTag("policy_sheet"),
            insideMargin = DpSize(0.dp, 0.dp),
            enableNestedScroll = false,
            renderInRootScaffold = false,
            onDismissRequest = onDismiss,
            onDismissFinished = { if (document == null) retained = null },
        ) {
            key(shownDocument) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(stringResource(R.string.policy_date, shownDocument.date), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(stringResource(shownDocument.body), style = MiuixTheme.textStyles.body1, modifier = Modifier.testTag("policy_body"))
                    TextButton(
                        text = stringResource(R.string.policy_project), enabled = document != null,
                        onClick = { linkError = runCatching { uriHandler.openUri(projectUrl) }.isFailure },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                    if (linkError) Text(stringResource(R.string.update_error_link), color = MiuixTheme.colorScheme.error)
                }
            }
        }
    }
}
