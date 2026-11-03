/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

/** Intent is explicit; callers never supply dialog button colors or composable buttons. */
enum class DialogActionTone { Normal, Destructive }

data class RiseDialogAction(
    val text: String,
    val onClick: () -> Unit,
    val tone: DialogActionTone,
    val enabled: Boolean = true
) {
    fun perform() { if (enabled) onClick() }
}

internal data class DialogActionPalette(val background: Long, val foreground: Long)

internal const val RiseNormalActionColor = 0xFF0066CCL

private fun blend(color: Long, target: Long, amount: Float): Long {
    var result = 0xFF000000L
    for (shift in listOf(16, 8, 0)) {
        val a = (color shr shift) and 255
        val b = (target shr shift) and 255
        result = result or ((a + (b - a) * amount).toLong() shl shift)
    }
    return result
}

/** Opaque surfaces stay distinct from labels, including on the transparent glass dialog. */
internal fun dialogActionPalette(
    dark: Boolean,
    emphasized: Boolean,
    destructive: Boolean,
    enabled: Boolean,
    errorColor: Long,
    primaryColor: Long = if (dark) 0xFF0091FF else 0xFF0088FF
): DialogActionPalette {
    val neutral = if (dark) 0xFF303238 else 0xFFFFFFFF
    // User preference: use the current theme hue without deepening the button.
    val accent = if (destructive) errorColor else primaryColor
    val background = if (emphasized) accent else neutral
    val foreground = if (emphasized) 0xFFFFFFFF else if (destructive) errorColor
        else if (dark) 0xFFE8EAED else 0xFF1A1C1E
    if (enabled) return DialogActionPalette(background, foreground)
    // Dim the palette itself, keeping the complete surface opaque and the label readable.
    return DialogActionPalette(blend(background, neutral, .45f), blend(foreground, background, .25f))
}

internal data class TopActionPlacement(val directIndices: List<Int>, val overflowIndices: List<Int>)

/** Caller order is priority order; overflow consumes one of the available touch targets. */
internal fun topActionPlacement(actionCount: Int, availableSlots: Int): TopActionPlacement {
    val count = actionCount.coerceAtLeast(0)
    val slots = availableSlots.coerceAtLeast(1)
    val direct = if (count <= slots) count else (slots - 1).coerceAtLeast(0)
    return TopActionPlacement((0 until direct).toList(), (direct until count).toList())
}
