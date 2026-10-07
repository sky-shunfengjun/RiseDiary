package com.risediary.app.ui.components

import kotlin.math.max

internal fun uiActionHeightDp(fontScale: Float): Float {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return max(48f, 24f * scale + 24f)
}

internal fun dialogActionsShouldStack(widthDp: Float, fontScale: Float): Boolean {
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    return !widthDp.isFinite() || widthDp < 228f || scale > 1.3f
}

internal fun bottomActionPaddingDp(measuredHeightDp: Float): Float =
    if (measuredHeightDp.isFinite() && measuredHeightDp > 0f) max(104f, measuredHeightDp + 36f) else 104f
