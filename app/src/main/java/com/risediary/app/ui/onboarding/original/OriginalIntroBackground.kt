/*
 * Copyright (C) 2023-2026 HyperCeiler Contributions.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Adapted from RenderViewLayout.java and GlowController.java at
 * 5e4686069dd7ab1f3697e256d5fc7d68fb73e317. Public Android capability fallback
 * replaces device checks; the ViewModel's clock replaces the private timer.
 */
package com.risediary.app.ui.onboarding.original

import android.app.ActivityManager
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import com.risediary.app.ui.onboarding.IntroBackend
import com.risediary.app.ui.onboarding.OnboardingVisualFrame
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

internal interface OriginalGlowRenderer {
    fun bind(frame: OnboardingVisualFrame, width: Int, height: Int, centerYFraction: Float): RenderEffect
}

/** Original 0.2-resolution surface, inverse-scaled to fill its single scene container. */
internal class OriginalIntroBackground(context: Context) : FrameLayout(context) {
    private val surface = GlowSurface(context)
    private var latest: OnboardingVisualFrame? = null
    private var presented: OnboardingVisualFrame? = null
    private var centerYFraction = 0.4f
    private var active = false
    private var released = false
    private var shaderAttempted = false
    private var shaderFailed = false
    private var canvasFailed = false
    private var renderer: OriginalGlowRenderer? = null
    private val lowMemory = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }.getOrDefault(false)

    var backend: IntroBackend = IntroBackend.CANVAS
        private set

    init {
        clipChildren = true
        clipToPadding = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(frame: OnboardingVisualFrame, centerInLocal: PointF) {
        if (released) return
        latest = frame
        if (height > 0 && centerInLocal.y.isFinite()) {
            centerYFraction = (centerInLocal.y / height).coerceIn(0f, 1f)
        }
        if (active) updateSurface()
    }

    fun setActive(active: Boolean) {
        if (released) return
        this.active = active
        if (active) updateSurface()
    }

    fun release() {
        if (released) return
        released = true
        active = false
        latest = null
        presented = null
        renderer = null
        surface.setRenderEffect(null)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val scaledWidth = ceil(width * CHILD_SCALE).toInt().coerceAtLeast(1)
        val scaledHeight = ceil(height * CHILD_SCALE).toInt().coerceAtLeast(1)
        val x = (width - scaledWidth) / 2
        val y = (height - scaledHeight) / 2
        surface.layout(x, y, x + scaledWidth, y + scaledHeight)
        surface.pivotX = scaledWidth / 2f
        surface.pivotY = scaledHeight / 2f
        surface.scaleX = 1f / CHILD_SCALE
        surface.scaleY = 1f / CHILD_SCALE
        if (active) updateSurface()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active) updateSurface()
    }

    private fun updateSurface() {
        if (released || !active || width <= 0 || height <= 0 || surface.width <= 0 || surface.height <= 0) return
        val frame = latest ?: return
        presented = frame
        if (!lowMemory && Build.VERSION.SDK_INT >= 33 && isHardwareAccelerated &&
            !shaderAttempted && !shaderFailed) {
            shaderAttempted = true
            try {
                renderer = createShaderRenderer()
            } catch (_: Exception) {
                shaderFailed = true
            } catch (_: LinkageError) {
                shaderFailed = true
            } catch (_: OutOfMemoryError) {
                shaderFailed = true
                canvasFailed = true
            }
        }
        backend = selectIntroBackend(Build.VERSION.SDK_INT, isHardwareAccelerated,
            renderer != null && !shaderFailed, !canvasFailed, lowMemory)
        if (backend == IntroBackend.ORIGINAL_SHADER) {
            try {
                surface.setRenderEffect(renderer!!.bind(frame, surface.width, surface.height, centerYFraction))
            } catch (_: Exception) {
                useCanvasOrStatic()
            } catch (_: LinkageError) {
                useCanvasOrStatic()
            } catch (_: OutOfMemoryError) {
                canvasFailed = true
                useCanvasOrStatic()
            }
        } else {
            surface.setRenderEffect(null)
        }
        surface.invalidate()
    }

    @DoNotInline
    @RequiresApi(33)
    private fun createShaderRenderer(): OriginalGlowRenderer = OriginalGlowPainterApi33(context)

    private fun useCanvasOrStatic() {
        shaderFailed = true
        renderer = null
        surface.setRenderEffect(null)
        backend = selectIntroBackend(Build.VERSION.SDK_INT, isHardwareAccelerated, false, !canvasFailed, lowMemory)
    }

    private inner class GlowSurface(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (backend == IntroBackend.ORIGINAL_SHADER) {
                canvas.drawColor(Color.BLACK)
                return
            }
            val frame = presented
            if (backend == IntroBackend.STATIC || frame == null) {
                canvas.drawColor(STATIC_COLOR)
                return
            }
            try {
                drawCompatibleFrame(canvas, frame)
            } catch (_: Exception) {
                canvasFailed = true
                backend = IntroBackend.STATIC
                canvas.drawColor(STATIC_COLOR)
            } catch (_: OutOfMemoryError) {
                canvasFailed = true
                backend = IntroBackend.STATIC
                canvas.drawColor(STATIC_COLOR)
            }
        }

        private fun drawCompatibleFrame(canvas: Canvas, frame: OnboardingVisualFrame) {
            val seconds = OriginalMotionCurves.glowSeconds(frame.introMillis)
            val maxDimension = max(width, height).coerceAtLeast(1).toFloat()
            val shift = sin(seconds * 0.12f) * width * 0.15f
            paint.blendMode = BlendMode.SRC_OVER
            paint.alpha = 255
            paint.shader = LinearGradient(shift, 0f, width.toFloat() + shift, height.toFloat(),
                intArrayOf(Color.rgb(245, 40, 40), STATIC_COLOR, Color.rgb(77, 74, 215)),
                floatArrayOf(0f, 0.47f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            // Original brightness layer has a 0.25 minimum; noise becomes a stable tint here.
            canvas.drawColor(Color.argb(64, 255, 255, 255))
            if (!frame.showAdmission) return

            val ring = OriginalMotionCurves.circleFrame(seconds)
            val cx = width / 2f
            val cy = height * centerYFraction
            if (ring.ringVisible && ring.outerRadius > 0f) {
                val colors = IntArray(96) { index ->
                    val distance = ring.outerRadius * index / 95f
                    val gradient = ((ring.outerRadius - distance) / 0.4f).coerceIn(0f, 1f)
                    val amplitude = (4f * gradient * (1f - gradient)).pow(3)
                    fun channel(offset: Float): Int =
                        ((0.5f + 0.5f * cos(6.28318f * (gradient + 0.25f + offset))) *
                            amplitude * 255f).toInt().coerceIn(0, 255)
                    Color.rgb(channel(0f), channel(0.1f), channel(0.2f))
                }
                paint.shader = RadialGradient(cx, cy, ring.outerRadius * maxDimension,
                    colors, null, Shader.TileMode.CLAMP)
                paint.blendMode = BlendMode.SCREEN
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.blendMode = BlendMode.PLUS
                paint.alpha = 10 // Original additive blend = 0.04.
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            paint.blendMode = BlendMode.SRC_OVER
            paint.alpha = 255
            if (ring.maskRadius <= -0.3f) {
                paint.shader = null
                paint.color = Color.BLACK
            } else {
                val extent = (ring.maskRadius + 0.3f).coerceAtLeast(0.0001f)
                val colors = IntArray(64) { index ->
                    val distance = extent * index / 63f
                    val t = ((distance - (ring.maskRadius - 0.3f)) / 0.6f).coerceIn(0f, 1f)
                    val alpha = (t * t * (3f - 2f * t) * 255f).toInt()
                    Color.argb(alpha, 0, 0, 0)
                }
                paint.shader = RadialGradient(cx, cy, extent * maxDimension, colors, null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.blendMode = BlendMode.SRC_OVER
            paint.alpha = 255
        }
    }

    private companion object {
        const val CHILD_SCALE = 0.2f
        val STATIC_COLOR: Int = Color.rgb(154, 168, 245)
    }
}
