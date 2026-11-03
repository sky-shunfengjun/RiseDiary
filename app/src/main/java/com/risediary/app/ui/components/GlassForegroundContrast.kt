package com.risediary.app.ui.components

import kotlin.math.pow

/** Compare black/white contrast against the captured background and its existing glass tint. */
internal fun glassForegroundIsLight(pixels: IntArray, previousIsLight: Boolean, surfaceArgb: Int = 0): Boolean {
    val opaque = pixels.filter { it ushr 24 != 0 }
    if (opaque.isEmpty()) return previousIsLight
    val tintAlpha = (surfaceArgb ushr 24) / 255.0
    fun channel(pixel: Int, shift: Int): Double {
        val base = (pixel ushr shift and 255) / 255.0
        val tint = (surfaceArgb ushr shift and 255) / 255.0
        val value = base * (1 - tintAlpha) + tint * tintAlpha
        return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
    }
    val luminance = opaque.sumOf { .2126 * channel(it, 16) + .7152 * channel(it, 8) + .0722 * channel(it, 0) } / opaque.size
    // Within this narrow band both choices meet 4.5:1; retain the last color to avoid flicker.
    return when {
        luminance < .175 -> true
        luminance > .183 -> false
        else -> previousIsLight
    }
}
