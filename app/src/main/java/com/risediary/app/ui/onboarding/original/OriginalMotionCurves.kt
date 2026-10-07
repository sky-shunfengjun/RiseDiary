/*
 * Copyright (C) 2023-2026 HyperCeiler Contributions.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Adapted from AnimHelper.java, GlowController.java and glow.glsl at
 * 5e4686069dd7ab1f3697e256d5fc7d68fb73e317. Folme is replaced with pure curves.
 */
package com.risediary.app.ui.onboarding.original

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.risediary.app.ui.onboarding.NativeRevealGeometry
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object OriginalMotionCurves {
    const val INTRO_DURATION = 1790f

    fun logoScale(millis: Float): Float {
        val elapsed = nonNegative(millis)
        return if (elapsed <= 440f) {
            0.5f + 0.45f * sin((elapsed / 440f) * PI.toFloat() / 2f)
        } else {
            0.95f + 0.05f * cubicOut((elapsed - 440f) / 700f)
        }
    }

    // Upstream delays alpha by 60ms without an explicit Folme duration.
    // The existing 300ms fade is the public, deterministic replacement.
    fun logoAlpha(millis: Float): Float = ((nonNegative(millis) - 60f) / 300f).coerceIn(0f, 1f)
    fun arrowAlpha(millis: Float): Float = cubicOut((nonNegative(millis) - 1340f) / 450f)
    fun arrowScale(millis: Float): Float = 0.9f + 0.1f * arrowAlpha(millis)

    /** Original controller: first 0 -> 120s, then ping-pong 120 -> 2 -> 120s. */
    fun glowSeconds(millis: Float): Float {
        val seconds = nonNegative(millis) / 1000f
        if (seconds <= 120f) return seconds
        val phase = (seconds - 120f) % 236f
        return if (phase <= 118f) 120f - phase else 2f + phase - 118f
    }

    /** Geometry of the complete upstream shader, reused only by the API31/32 fallback. */
    fun circleFrame(seconds: Float): OriginalGlowCircleFrame {
        val raw = nonNegative(seconds) * 0.9f
        val t = raw.coerceIn(0f, 1f)
        val outer = t / (1.4f - t * 0.4f)
        // Do not clamp the lower edge: the negative initial radius covers frame zero.
        val radius = min(raw - 0.3f, 1f)
        val mask = 1f - (1f - radius) * (1f - radius)
        return OriginalGlowCircleFrame(outer, mask, raw <= 1f)
    }

    fun circleDistance(dx: Float, dy: Float, width: Float, height: Float): Float =
        hypot(dx, dy) / max(max(nonNegative(width), nonNegative(height)), 1f)

    private fun cubicOut(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    private fun nonNegative(value: Float): Float = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
}

internal data class OriginalGlowCircleFrame(val outerRadius: Float, val maskRadius: Float, val ringVisible: Boolean)

/** Public in-container reveal, replacing the upstream MIUI cross-Activity API. */
internal fun revealGeometry(buttonBounds: Rect, viewport: Size): NativeRevealGeometry {
    val valid = listOf(buttonBounds.left, buttonBounds.top, buttonBounds.right, buttonBounds.bottom,
        viewport.width, viewport.height).all(Float::isFinite)
    if (!valid || viewport.width <= 0f || viewport.height <= 0f ||
        buttonBounds.width <= 0f || buttonBounds.height <= 0f) {
        return NativeRevealGeometry(0f, 0f, 0f, 0f)
    }
    val x = buttonBounds.center.x
    val y = buttonBounds.center.y
    val initial = min(buttonBounds.width, buttonBounds.height) / 2f
    val final = max(
        max(hypot(x, y), hypot(viewport.width - x, y)),
        max(hypot(x, viewport.height - y), hypot(viewport.width - x, viewport.height - y)),
    ).coerceAtLeast(initial)
    return NativeRevealGeometry(x, y, initial, final)
}
