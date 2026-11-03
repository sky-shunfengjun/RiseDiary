/*
 * Adapted from Kyant0/AndroidLiquidGlass (kmp), AdaptiveLuminanceGlassContent.
 * SPDX-License-Identifier: GPL-3.0-only AND Apache-2.0
 */
package com.risediary.app.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class GlassBackdropContrast(val capture: GraphicsLayer?, val colors: List<Color>)

/** Sample only the small control background, before its icons/text/selection are drawn. */
@Composable
internal fun rememberGlassBackdropContrast(columns: Int = 1, surface: Color = Color.Transparent, enabled: Boolean = true): GlassBackdropContrast {
    val layer = rememberGraphicsLayer()
    val dark = LocalRiseDarkTheme.current
    var light by remember(layer, columns, dark) { mutableStateOf(List(columns) { dark }) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = enabled && LocalPageEffectsActive.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val tint = surface.toArgb()
    LaunchedEffect(layer, columns, dark, tint, active) {
        if (!active) return@LaunchedEffect
        withFrameNanos { }
        while (isActive) {
            if (layer.size.width > 0 && layer.size.height > 0) {
                try {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    val pixels = try { thumbnailPixels(bitmap, columns * 5, 5) } finally { bitmap.recycle() }
                    light = List(columns) { column ->
                        val region = IntArray(25) { index -> pixels[(index / 5) * columns * 5 + column * 5 + index % 5] }
                        glassForegroundIsLight(region, light[column], tint)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An unavailable/disposed capture keeps the last readable color; retry while visible.
                }
            }
            delay(350)
        }
    }
    return GlassBackdropContrast(if (active) layer else null, light.map { if (it) Color.White else Color.Black })
}

private fun thumbnailPixels(bitmap: Bitmap, width: Int, height: Int): IntArray {
    val thumbnail = Bitmap.createScaledBitmap(bitmap, width, height, false)
    try {
        // GraphicsLayer may return a hardware bitmap; read only a tiny software copy.
        val software = checkNotNull(thumbnail.copy(Bitmap.Config.ARGB_8888, false))
        try { return IntArray(width * height).also { software.getPixels(it, 0, width, 0, 0, width, height) } }
        finally { software.recycle() }
    } finally { if (thumbnail !== bitmap) thumbnail.recycle() }
}
