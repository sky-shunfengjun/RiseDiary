/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.risediary.app.ui.about

import com.risediary.app.ui.components.rememberTopBlurProgress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.shape.RoundedCornerShape

internal val thirdPartyLibCardCornerRadius = CardDefaults.CornerRadius

@Composable
fun ThirdPartyLibsScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current

    val listState = rememberLazyListState()

    SecondaryPageScaffold(
        topBlurProgress = rememberTopBlurProgress(listState),
        title = stringResource(R.string.about_libraries_title),
        onBack = { navigator.pop() }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null
        ) {
            item(key = "thanks") {
                Text(
                    text = stringResource(R.string.about_libraries_acknowledgement),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MiuixTheme.textStyles.paragraph,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            item(key = "libraries") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = thirdPartyLibCardCornerRadius,
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        thirdPartyProjects.forEachIndexed { index, project ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            val rowShape = RoundedCornerShape(
                                topStart = if (index == 0) thirdPartyLibCardCornerRadius else 0.dp,
                                topEnd = if (index == 0) thirdPartyLibCardCornerRadius else 0.dp,
                                bottomStart = if (index == thirdPartyProjects.lastIndex) thirdPartyLibCardCornerRadius else 0.dp,
                                bottomEnd = if (index == thirdPartyProjects.lastIndex) thirdPartyLibCardCornerRadius else 0.dp
                            )
                            ArrowPreference(
                                title = project.name,
                                summary = project.url,
                                modifier = Modifier.clip(rowShape),
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                onClick = { runCatching { uriHandler.openUri(project.url) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
