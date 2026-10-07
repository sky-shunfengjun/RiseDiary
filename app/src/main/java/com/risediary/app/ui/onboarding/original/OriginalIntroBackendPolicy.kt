/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding.original

import com.risediary.app.ui.onboarding.IntroBackend

/** Brand-free capability choice. The caller attempts the guarded shader at most once. */
internal fun selectIntroBackend(
    api: Int, hardware: Boolean, shaderReady: Boolean,
    canvasReady: Boolean = true, lowMemory: Boolean = false,
): IntroBackend = when {
    lowMemory -> IntroBackend.STATIC
    api >= 33 && hardware && shaderReady -> IntroBackend.ORIGINAL_SHADER
    canvasReady -> IntroBackend.CANVAS
    else -> IntroBackend.STATIC
}
