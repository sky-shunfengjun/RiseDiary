/*
 * Copyright (C) 2023-2026 HyperCeiler Contributions.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Adapted from GlowPainter.java at 5e4686069dd7ab1f3697e256d5fc7d68fb73e317.
 * Complete shader and its defaults are preserved; time is supplied by the scene.
 */
package com.risediary.app.ui.onboarding.original

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import com.risediary.app.R
import com.risediary.app.ui.onboarding.OnboardingVisualFrame

/** All API33-only types stay in this class. Never constructed below the SDK guard. */
@RequiresApi(33)
internal class OriginalGlowPainterApi33(context: Context) : OriginalGlowRenderer {
    private val shader = RuntimeShader(
        context.resources.openRawResource(R.raw.oobe_original_glow).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    init {
        shader.setFloatUniform("uScale2", 0.82f)
        shader.setFloatUniform("uSpeed2", 0.49f)
        shader.setFloatUniform("uColorInMin", 0.3f)
        shader.setFloatUniform("uColorInMax", 1.0f)
        shader.setFloatUniform("uColorOutMin", 0.3f)
        shader.setFloatUniform("uColorOutMax", 0.86f)
        shader.setFloatUniform("uColorMidPoint", 0.47f)
        shader.setFloatUniform("uUseOklab", 1.0f)
        shader.setFloatUniform("uColorBlack", 0.961f, 0.157f, 0.157f)
        shader.setFloatUniform("uColorMid", 0.604f, 0.659f, 0.961f)
        shader.setFloatUniform("uColorWhite", 0.302f, 0.29f, 0.843f)
        shader.setFloatUniform("uScale", 1.3f)
        shader.setFloatUniform("uSpeed", 0.4f)
        shader.setFloatUniform("uBrightnessInMin", 0.25f)
        shader.setFloatUniform("uBrightnessInMax", 1.0f)
        shader.setFloatUniform("uBrightnessOutMin", 0.25f)
        shader.setFloatUniform("uBrightnessOutMax", 1.0f)
        shader.setFloatUniform("uShowCircle", 1.0f)
        shader.setFloatUniform("uCircleThickness", 0.4f)
        shader.setFloatUniform("uCircleFinalRadius", 1.0f)
        shader.setFloatUniform("uCircleYOffset", 0.1f)
        shader.setFloatUniform("uCircleSpeed", 0.9f)
        shader.setFloatUniform("uCircleColorFreq", 1.0f)
        shader.setFloatUniform("uCircleColorSpeed", 0.0f)
        shader.setFloatUniform("uCircleEasing", 1.4f)
        shader.setFloatUniform("uCircleAnimationOffset", 0.0f)
        shader.setFloatUniform("uMaskDelay", 0.3f)
        shader.setFloatUniform("uMaskThickness", 0.3f)
        shader.setFloatUniform("uCircleScreenBlend", 1.0f)
        shader.setFloatUniform("uCircleAddBlend", 0.04f)
        shader.setFloatUniform("uCircleColorOffset", 0.25f)
        shader.setFloatUniform("uCircleUVDistort", 0.0f)
        shader.setFloatUniform("uColorToDistortWidthRatio", 0.6f)
        shader.setFloatUniform("uDistortStartTime", 0.2f)
        shader.setFloatUniform("uDistortEndTime", 0.3f)
        shader.setFloatUniform("uDistortStart", 0.0f)
        shader.setFloatUniform("uDistortEnd", 1.0f)
        shader.setFloatUniform("uStripeFrequency", 0.0f)
        shader.setFloatUniform("uStripeStrengthX", 0.0f)
        shader.setFloatUniform("uStripeStrengthY", 0.0f)
        shader.setFloatUniform("uStripeUVDistort", 0.0f)
    }

    override fun bind(
        frame: OnboardingVisualFrame, width: Int, height: Int, centerYFraction: Float,
    ): RenderEffect {
        shader.setFloatUniform("uTime", OriginalMotionCurves.glowSeconds(frame.introMillis))
        shader.setFloatUniform("uResolution", width.coerceAtLeast(1).toFloat(), height.coerceAtLeast(1).toFloat())
        shader.setFloatUniform("uCircleYOffset", 0.5f - centerYFraction)
        shader.setFloatUniform("uShowCircle", if (frame.showAdmission) 1f else 0f)
        // Preserve the original controller's uniform snapshot for every visible frame.
        return RenderEffect.createShaderEffect(shader)
    }
}
