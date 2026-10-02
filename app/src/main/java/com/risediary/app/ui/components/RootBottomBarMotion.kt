/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Root-owned; survives entry culling and continues from its current position. */
internal class RootBottomBarMotion(rootVisibleInitially: Boolean) {
    private val progress = Animatable(if (rootVisibleInitially) 0f else 1f)
    val value: Float get() = progress.value

    suspend fun animateRootVisibility(rootVisible: Boolean) {
        val target = if (rootVisible) 0f else 1f
        if (progress.value == target) return
        progress.animateTo(
            target,
            tween(if (rootVisible) 260 else 300, easing = FastOutSlowInEasing),
        )
    }
}

/** A committed pop starts the reveal alongside exit; previews and cancellations keep it hidden. */
@Composable
internal fun rememberRootBottomBarProgress(
    motion: HyperIslandNavigationMotion,
    isMain: Boolean,
): () -> Float {
    val currentIsMain by rememberUpdatedState(isMain)
    val rootVisible by remember(motion) {
        derivedStateOf { currentIsMain && motion.rootBarVisible }
    }
    val bar = remember(motion) { RootBottomBarMotion(rootVisible) }
    LaunchedEffect(rootVisible) { bar.animateRootVisibility(rootVisible) }
    return remember(bar) { { bar.value } }
}
