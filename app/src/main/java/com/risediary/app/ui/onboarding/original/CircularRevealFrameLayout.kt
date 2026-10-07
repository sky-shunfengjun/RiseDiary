/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding.original

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.view.MotionEvent
import android.widget.FrameLayout
import com.risediary.app.ui.onboarding.NativeRevealGeometry

/** One clip for the complete scene draw: background, content and actions move together. */
internal class CircularRevealFrameLayout(context: Context) : FrameLayout(context) {
    private var geometry: NativeRevealGeometry? = null
    private var progress = 1f
    private var visibleInside = true
    private val clipPath = Path()
    private var priorAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO

    fun bindReveal(geometry: NativeRevealGeometry, progress: Float, visibleInside: Boolean) {
        if (!geometry.isUsable || !progress.isFinite()) {
            clearReveal()
            return
        }
        if (this.geometry == null) priorAccessibility = importantForAccessibility
        this.geometry = geometry
        this.progress = progress.coerceIn(0f, 1f)
        this.visibleInside = visibleInside
        importantForAccessibility = if (isWholeSceneVisible()) priorAccessibility
            else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        invalidate()
    }

    fun clearReveal() {
        geometry = null
        progress = 1f
        visibleInside = true
        importantForAccessibility = priorAccessibility
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        val g = geometry
        if (g == null || isWholeSceneVisible()) {
            super.draw(canvas)
            return
        }
        if (!visibleInside && progress >= 1f) return
        val radius = g.initialRadius + (g.finalRadius - g.initialRadius) * progress
        clipPath.rewind()
        if (!visibleInside) {
            clipPath.fillType = Path.FillType.EVEN_ODD
            clipPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        } else {
            clipPath.fillType = Path.FillType.WINDING
        }
        clipPath.addCircle(g.centerX, g.centerY, radius, Path.Direction.CW)
        val saved = canvas.save()
        try {
            canvas.clipPath(clipPath)
            super.draw(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (geometry != null && !isWholeSceneVisible()) false else super.dispatchTouchEvent(event)

    private fun isWholeSceneVisible(): Boolean = visibleInside && progress >= 1f
}
