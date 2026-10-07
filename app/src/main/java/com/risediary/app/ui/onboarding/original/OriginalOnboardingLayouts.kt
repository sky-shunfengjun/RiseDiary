/*
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * Copyright (C) 2026 sky-shunfengjun
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Adapted from HyperCeiler provision_startup_layout.xml / dimensions / button styles
 * at 5e4686069dd7ab1f3697e256d5fc7d68fb73e317. The original weights and sizes remain;
 * fan / MIUI blur widgets are replaced by public Android Views and existing AppIcons.
 */
package com.risediary.app.ui.onboarding.original

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.risediary.app.R
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.OnboardingStep
import com.risediary.app.ui.onboarding.GuideSceneSpec
import com.risediary.app.ui.onboarding.GuideWelcomeGraphic
import androidx.compose.foundation.shape.CircleShape
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import kotlin.math.ceil

internal val OriginalOnboardingAccent = Color(0xFF3482FF)

@Composable
internal fun originalOnboardingBackground(): Color =
    if (LocalRiseDarkTheme.current) Color(0xFF141414) else Color(0xFFF7F7F7)

@Composable
internal fun originalOnboardingSurface(): Color =
    if (LocalRiseDarkTheme.current) Color(0xFF242424) else Color.White

@Composable
internal fun originalOnboardingSecondary(): Color =
    if (LocalRiseDarkTheme.current) Color(0xFF303030) else Color(0xFFEFEFEF)

@Composable
internal fun originalOnboardingText(): Color =
    if (LocalRiseDarkTheme.current) Color.White else Color.Black

@Composable
internal fun originalOnboardingSummary(): Color =
    originalOnboardingText().copy(alpha = if (LocalRiseDarkTheme.current) 0.6f else 0.5f)

internal data class OriginalWelcomeViews(
    val root: View,
    val logo: View,
    val title: TextView,
    val next: View,
)

/** Native welcome owns one accessible action; the existing vector is decorative only. */
internal fun createOriginalWelcome(context: Context, onNext: () -> Unit, spec: GuideSceneSpec = GuideSceneSpec()): OriginalWelcomeViews {
    val inflationRoot = FrameLayout(context)
    val root = LayoutInflater.from(context).inflate(R.layout.oobe_original_welcome, inflationRoot, false)
    val next = root.findViewById<FrameLayout>(R.id.oobe_original_next)
    val icon = ComposeView(context).apply {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Image(
                    rememberVectorPainter(AppIcons.ArrowBack), null,
                    Modifier.size(28.dp).rotate(180f), colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
    next.addView(icon, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    next.contentDescription = context.getString(R.string.oobe_next)
    ViewCompat.setAccessibilityDelegate(next, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = Button::class.java.name
        }
    })
    next.setOnClickListener { if (next.isEnabled && next.alpha > 0f) onNext() }
    root.findViewById<TextView>(R.id.oobe_original_title).setText(spec.welcomeTitle)
    root.findViewById<TextView>(R.id.oobe_original_subtitle).setText(spec.welcomeSubtitle)
    var hero: View = root.findViewById<ImageView>(R.id.oobe_original_logo)
    if (spec.welcomeGraphic == GuideWelcomeGraphic.UPDATE_CHECK) {
        val previous = hero
        val parent = previous.parent as android.view.ViewGroup
        val index = parent.indexOfChild(previous)
        val marker = ComposeView(context).apply {
            id = previous.id
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                Box(Modifier.fillMaxSize().background(OriginalOnboardingAccent, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(AppIcons.Check), null, Modifier.size(44.dp),
                        colorFilter = ColorFilter.tint(Color.White))
                }
            }
        }
        parent.removeView(previous)
        parent.addView(marker, index, previous.layoutParams)
        hero = marker
    }
    next.isEnabled = false
    next.isClickable = false
    next.isFocusable = false
    root.isSaveEnabled = false
    root.isSaveFromParentEnabled = false
    return OriginalWelcomeViews(
        root, hero,
        root.findViewById(R.id.oobe_original_title), next,
    )
}

/**
 * ScrollView measures its child without a height limit. A minimum based on the measured
 * text preserves the original 30:40:20 weights when they fit, and allows scrolling when
 * large text / landscape would otherwise overlap the title and arrow.
 */
class OriginalWelcomeColumn @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val label = findViewById<View>(R.id.oobe_original_logo_wrapper)
        if (label != null) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            label.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val logo = resources.getDimension(R.dimen.oobe_original_logo_size)
            val top = resources.getDimension(R.dimen.oobe_original_logo_text_gap)
            val gap = resources.getDimension(R.dimen.oobe_original_welcome_min_gap)
            val button = resources.getDimension(R.dimen.oobe_original_next_size) +
                2f * resources.getDimension(R.dimen.oobe_original_next_padding)
            minimumHeight = ceil(logo + (label.measuredHeight + top + gap + button) * 90f / 40f).toInt()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

@Composable
internal fun OriginalOnboardingBody(
    step: OnboardingStep,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    GuideSceneBody(step == OnboardingStep.WELCOME || step == OnboardingStep.COMPLETE, enabled, content)
}

@Composable
internal fun GuideSceneBody(hero: Boolean, enabled: Boolean, content: @Composable () -> Unit) {
    val background = if (hero) Color.Transparent else originalOnboardingBackground()
    Box(
        Modifier.fillMaxSize().background(background).then(
            if (enabled) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        ),
    ) { content() }
}
