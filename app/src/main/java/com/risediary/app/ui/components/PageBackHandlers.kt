/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

internal val LocalPageBackEnabled = staticCompositionLocalOf { true }
private val LocalAppBackOwner = staticCompositionLocalOf<NavigationEventDispatcherOwner?> { null }

/**
 * Capture the host BEFORE NavDisplay replaces the local with a disposable entry
 * dispatcher. A retained/moved page must never register on its former entry host.
 */
@Composable
internal fun StableNavigationBackHost(content: @Composable () -> Unit) {
    val owner = LocalNavigationEventDispatcherOwner.current
    CompositionLocalProvider(LocalAppBackOwner provides owner, content = content)
}

/** Explicit page gating replaces the entry child's dispatch arbitration. */
@Composable
internal fun PageBackScope(enabled: Boolean, content: @Composable () -> Unit) {
    val appOwner = LocalAppBackOwner.current
    if (appOwner != null) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides appOwner,
            LocalPageBackEnabled provides enabled,
            content = content,
        )
    } else {
        // Hosts without NavigationEvent use Android's activity-level legacy fallback.
        CompositionLocalProvider(LocalPageBackEnabled provides enabled, content = content)
    }
}

@Composable
internal fun PageBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled && LocalPageBackEnabled.current, onBack = onBack)
}
