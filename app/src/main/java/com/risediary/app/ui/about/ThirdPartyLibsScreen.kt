/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.risediary.app.ui.about

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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.shape.RoundedCornerShape

private data class ThirdPartyLib(val name: String, val url: String)

internal val thirdPartyLibCardCornerRadius = CardDefaults.CornerRadius

private val thirdPartyLibs = listOf(
    ThirdPartyLib("Jetpack Compose", "https://developer.android.com/jetpack/compose"),
    ThirdPartyLib("Miuix", "https://github.com/YuKongA/miuix"),
    ThirdPartyLib("AndroidLiquidGlass", "https://github.com/Kyant0/AndroidLiquidGlass"),
    ThirdPartyLib("Navigation3", "https://developer.android.com/jetpack/androidx/releases/navigation3"),
    ThirdPartyLib("Room", "https://developer.android.com/jetpack/androidx/releases/room"),
    ThirdPartyLib("DataStore", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    ThirdPartyLib("Hilt", "https://dagger.dev/hilt/"),
    ThirdPartyLib("WorkManager", "https://developer.android.com/jetpack/androidx/releases/work"),
    ThirdPartyLib("AndroidX Biometric", "https://developer.android.com/jetpack/androidx/releases/biometric"),
    ThirdPartyLib("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
    ThirdPartyLib("Vico", "https://github.com/patrykandpatrick/vico"),
    ThirdPartyLib("KernelSU-Style-UI-Kit", "https://github.com/chenaizhang/KernelSU-Style-UI-Kit"),
)

@Composable
fun ThirdPartyLibsScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current

    SecondaryPageScaffold(
        title = stringResource(R.string.about_libraries_title),
        onBack = { navigator.pop() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null
        ) {
            item(key = "libraries") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = thirdPartyLibCardCornerRadius,
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        thirdPartyLibs.forEachIndexed { index, lib ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            val rowShape = when (index) {
                                0 -> RoundedCornerShape(
                                    topStart = thirdPartyLibCardCornerRadius,
                                    topEnd = thirdPartyLibCardCornerRadius
                                )
                                thirdPartyLibs.lastIndex ->
                                    RoundedCornerShape(
                                        bottomStart = thirdPartyLibCardCornerRadius,
                                        bottomEnd = thirdPartyLibCardCornerRadius
                                    )
                                else -> RoundedCornerShape(0.dp)
                            }
                            ArrowPreference(
                                title = lib.name,
                                summary = lib.url,
                                modifier = Modifier
                                    .clip(rowShape),
                                insideMargin = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 16.dp
                                ),
                                onClick = {
                                    runCatching { uriHandler.openUri(lib.url) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
