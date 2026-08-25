/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.risediary.app.ui.about

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.BuildConfig
import com.risediary.app.R
import com.risediary.app.ui.about.effect.BgEffectBackground
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.PageTopBar
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Route
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import com.risediary.app.ui.theme.backgroundBrush
import com.risediary.app.ui.update.UpdateStatusDialog
import com.risediary.app.ui.update.UpdateStatusDialogState
import com.risediary.app.update.UpdateCheckState
import com.risediary.app.update.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AboutScreen(
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var showStatusDialog by remember { mutableStateOf(false) }
    val isChecking = updateState is UpdateCheckState.Checking
    val updateButtonDescription = stringResource(R.string.update_button_content_description)
    val githubUrl = stringResource(R.string.about_github_url)
    val qqGroupUrl = stringResource(R.string.about_qq_group_url)
    val githubContentDescription = stringResource(R.string.about_github_content_description)
    val qqGroupContentDescription = stringResource(R.string.about_qq_group_content_description)

    val lazyListState = rememberLazyListState()
    val isDark = LocalRiseDarkTheme.current
    val pageBackground = backgroundBrush()
    val currentPageBackground by rememberUpdatedState(pageBackground)
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(brush = currentPageBackground)
        drawContent()
    }

    val scrollProgress by remember {
        derivedStateOf {
            val first = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
            when {
                first == null -> 0f
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                first.size <= 0 -> 0f
                else -> (lazyListState.firstVisibleItemScrollOffset.toFloat() / first.size)
                    .coerceIn(0f, 1f)
            }
        }
    }
    val titleAlpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)

    val enableShader = isRuntimeShaderSupported()
    val effectBackground = enableShader && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            BgEffectBackground(
                dynamicBackground = effectBackground,
                effectBackground = effectBackground,
                isFullSize = true,
                modifier = Modifier.fillMaxSize(),
                alpha = { 1f - scrollProgress }
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    overscrollEffect = null
                ) {
                    item(key = "logo") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 88.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                modifier = Modifier.size(100.dp),
                                painter = painterResource(R.drawable.app_icon),
                                contentDescription = null,
                            )
                            Text(
                                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
                                text = stringResource(R.string.app_name),
                                color = MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 35.sp,
                            )
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    item(key = "links") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            cornerRadius = 24.dp,
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column {
                                ArrowPreference(
                                    title = stringResource(R.string.about_github_title),
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 24.dp,
                                                topEnd = 24.dp
                                            )
                                        )
                                        .semantics {
                                            contentDescription = githubContentDescription
                                        },
                                    onClick = {
                                        runCatching {
                                            uriHandler.openUri(githubUrl)
                                        }
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                ArrowPreference(
                                    title = stringResource(R.string.about_qq_group_title),
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                bottomStart = 24.dp,
                                                bottomEnd = 24.dp
                                            )
                                        )
                                        .semantics {
                                            contentDescription = qqGroupContentDescription
                                        },
                                    onClick = {
                                        runCatching {
                                            uriHandler.openUri(qqGroupUrl)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item(key = "libraries") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            cornerRadius = CardDefaults.CornerRadius,
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surfaceContainer
                            ),
                            pressFeedbackType = PressFeedbackType.None,
                            showIndication = true,
                            onClick = { navigator.push(Route.ThirdPartyLibs) }
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = aboutLibrariesEntryHorizontalPadding()
                                )
                            ) {
                                ArrowPreference(
                                    title = stringResource(R.string.about_libraries_title)
                                )
                            }
                        }
                    }

                    item(key = "info") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            colors = CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                AboutItem(
                                    stringResource(R.string.about_usage_title),
                                    stringResource(R.string.usage_disclaimer)
                                )
                                HorizontalDivider()
                                AboutItem(
                                    stringResource(R.string.about_data_privacy),
                                    stringResource(R.string.about_privacy_content)
                                )
                            }
                        }
                    }
                }
            }
        }

        PageTopBar(
            title = stringResource(R.string.about_title),
            onBack = { navigator.pop() },
            backdrop = pageBackdrop,
            titleAlpha = { titleAlpha }
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            LiquidGlassButton(
                onClick = {
                    showStatusDialog = true
                    updateViewModel.checkForUpdate(force = true)
                },
                backdrop = pageBackdrop,
                enabled = !isChecking,
                isInteractive = !isChecking,
                tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.075f),
                height = 52.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = updateButtonDescription
                    }
            ) {
                Icon(AppIcons.Refresh, contentDescription = null)
                Text(
                    if (isChecking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.update_check_button)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // The app-level host already shows UpdateAvailableDialog for the whole app;
    // composing it here too would race for the single dialog slot.
    if (showStatusDialog) {
        val status = when (updateState) {
            UpdateCheckState.UpToDate -> UpdateStatusDialogState.UP_TO_DATE
            UpdateCheckState.Failed -> UpdateStatusDialogState.FAILED
            else -> null
        }
        if (status != null) {
            UpdateStatusDialog(
                currentVersion = updateViewModel.currentVersion,
                state = status,
                onDismiss = {
                    showStatusDialog = false
                    updateViewModel.dismiss()
                }
            )
        }
    }
}

internal fun aboutLibrariesEntryHorizontalPadding() = 0.dp

@Composable
private fun AboutItem(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            title,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            desc,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}
