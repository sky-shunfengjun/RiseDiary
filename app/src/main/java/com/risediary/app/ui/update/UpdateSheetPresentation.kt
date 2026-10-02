/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.update

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Presentation only: native sheet gestures never change the update/download model. */
@Stable
class UpdateSheetPresentation {
    var visible by mutableStateOf(false)
        private set
    var retained by mutableStateOf(false)
        private set
    var backdropMultiplier by mutableStateOf(1f)
        private set
    var settlementId by mutableStateOf<Long?>(null)
        private set
    var openingId by mutableStateOf(0L)
        private set
    var openingMultiplier by mutableStateOf(1f)
        private set

    private enum class BackPhase { IDLE, IGNORED, GESTURE, SETTLING }
    private var phase = BackPhase.IDLE
    private var gestureInProgress = false
    private var nextSettlementId = 0L
    private var openingPending = false
    private var sheetHeight = 0
    private var windowHeight = 0
    private var sheetOffset = 0f
    private var settleOffset = 0f
    private var settleStrength = 1f
    private var settleHasMoved = false

    /** Keep the capture alive while miuix is still drawing its exit animation. */
    fun updateVisibility(value: Boolean) {
        if (value && !visible) {
            // The backdrop folds this previous multiplier into its opening spring once.
            openingMultiplier = (if (openingPending) openingMultiplier else 1f) * backdropMultiplier
            openingPending = true
            openingId++
            clearBackPreview()
        }
        visible = value
        if (value) retained = true
    }

    /** Acknowledge folding the old preview into the backdrop's spring, only for this opening. */
    fun onOpeningApplied(id: Long) {
        if (id == openingId) openingPending = false
    }

    /** Freeze the back target at gesture start; internal popup/page returns keep full backdrop strength. */
    fun onBackProgress(progress: Float, closesSheet: Boolean) {
        if (!progress.isFinite()) return
        if (!visible) {
            // Keep an in-flight gesture ignored if the app unlocks/reopens before its Idle.
            gestureInProgress = true
            phase = BackPhase.IGNORED
            settlementId = null
            return
        }
        if (phase != BackPhase.GESTURE && phase != BackPhase.IGNORED) {
            clearBackPreview()
            gestureInProgress = true
            phase = if (closesSheet) BackPhase.GESTURE else BackPhase.IGNORED
        }
        if (phase == BackPhase.GESTURE) {
            backdropMultiplier = 1f - progress.coerceIn(0f, 1f)
        }
    }

    /** Public Idle does not distinguish cancel from commit. Keep the last strength until motion. */
    fun onBackIdle() {
        gestureInProgress = false
        when (phase) {
            BackPhase.GESTURE -> {
                phase = BackPhase.SETTLING
                settleOffset = sheetOffset
                settleStrength = backdropMultiplier
                settleHasMoved = false
                settlementId = ++nextSettlementId
            }
            BackPhase.IGNORED -> {
                if (visible) clearBackPreview() else phase = BackPhase.IDLE
            }
            else -> Unit
        }
    }

    /** Bounds include miuix's graphics-layer translation; the resting sheet is bottom aligned. */
    fun onSheetBounds(top: Int, height: Int, windowHeight: Int) {
        if (height <= 0 || windowHeight <= 0) return
        if (sheetHeight > 0 && (sheetHeight != height || this.windowHeight != windowHeight)) {
            val wasGesture = phase == BackPhase.GESTURE || phase == BackPhase.IGNORED
            clearBackPreview()
            // A resized window cannot reuse a gesture measured against the old sheet.
            if (wasGesture) phase = BackPhase.IGNORED
        }
        sheetHeight = height
        this.windowHeight = windowHeight
        val offset = ((top.toFloat() - (windowHeight - height)) / height).coerceIn(0f, 1f)
        val previousOffset = sheetOffset
        sheetOffset = offset
        if (phase != BackPhase.SETTLING || offset == previousOffset) return
        if (offset == 0f) {
            clearBackPreview()
            return
        }
        if (offset == 1f) {
            backdropMultiplier = 0f
            settleHasMoved = true
            return
        }
        if (!settleHasMoved && settleOffset == 0f && settleStrength < 1f) {
            // Progress may arrive before the first translated layout. Adopt that first position
            // without a brightness jump, then follow the native rebound or exit from there.
            settleOffset = offset
        } else {
            backdropMultiplier = when {
                offset < settleOffset -> settleStrength + (1f - settleStrength) *
                    (1f - offset / settleOffset)
                settleOffset < 1f -> settleStrength * (1f - offset) / (1f - settleOffset)
                else -> settleStrength
            }.coerceIn(0f, 1f)
        }
        settleHasMoved = true
    }

    /** Resolve a zero-motion cancellation after the native rebound clock, never before it starts. */
    fun finishBackSettlement(id: Long) {
        if (settlementId == id && visible && sheetHeight > 0 && sheetOffset == 0f) {
            clearBackPreview()
        }
    }

    fun clearBackPreview() {
        phase = if (gestureInProgress) BackPhase.IGNORED else BackPhase.IDLE
        settlementId = null
        backdropMultiplier = 1f
        settleOffset = 0f
        settleStrength = 1f
        settleHasMoved = false
    }

    fun finishDismiss() {
        if (!visible) {
            retained = false
            // Visibility's spring can finish a frame later. Do not restore a committed preview
            // to full strength while that last background frame is still being drawn.
            val finalStrength = backdropMultiplier
            gestureInProgress = false
            clearBackPreview()
            backdropMultiplier = finalStrength
            sheetHeight = 0
            windowHeight = 0
            sheetOffset = 0f
        }
    }

    fun reset() {
        visible = false
        retained = false
        openingPending = false
        openingMultiplier = 1f
        gestureInProgress = false
        clearBackPreview()
        sheetHeight = 0
        windowHeight = 0
        sheetOffset = 0f
    }
}
