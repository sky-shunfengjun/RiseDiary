/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.Rect as AndroidRect
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.components.LocalPageEffectsActive
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.onboarding.original.*
import top.yukonga.miuix.kmp.basic.Icon

/** A stable native scene; only its contents transition. No cross-Activity or private APIs. */
@Composable
internal fun NativeOnboardingHost(
    ui: OnboardingUiState,
    frame: OnboardingVisualFrame,
    active: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onTransitionSettled: (Long, OnboardingStep) -> Unit,
    content: @Composable (OnboardingStep, Boolean) -> Unit,
    onGeometry: (Offset, Rect, Size) -> Unit = { _, _, _ -> },
    primaryEnabled: Boolean = true,
    introCenter: Offset = Offset.Unspecified,
) {
    val composition = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    val currentNext = rememberUpdatedState(onNext)
    val currentBack = rememberUpdatedState(onBack)
    val currentSettled = rememberUpdatedState(onTransitionSettled)
    val currentGeometry = rememberUpdatedState(onGeometry)
    val density = LocalDensity.current
    val backgroundColor = originalOnboardingBackground()
    val background = backgroundColor.toArgb()
    val chromeBackdrop = rememberLayerBackdrop()
    val foreground = originalOnboardingText().toArgb()
    val left = WindowInsets.safeDrawing.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val right = WindowInsets.safeDrawing.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val top = WindowInsets.safeDrawing.getTop(density)
    val bottom = maxOf(WindowInsets.safeDrawing.getBottom(density), WindowInsets.ime.getBottom(density))
    Box(Modifier.fillMaxSize()) {
        // Full-size source has no button-sized edges and never records its consumers.
        Box(Modifier.matchParentSize().layerBackdrop(chromeBackdrop).background(backgroundColor))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> NativeOnboardingScene(context, composition, currentContent,
                chromeBackdrop, { currentNext.value() }, { currentBack.value() },
                { id, step -> currentSettled.value(id, step) },
                { center, bounds, size -> currentGeometry.value(center, bounds, size) }) },
            update = { scene -> scene.bind(ui, frame, active, primaryEnabled, introCenter,
                background, foreground, density.fontScale, left, top, right, bottom) },
            onRelease = { it.release() },
        )
    }
}

// Always constructed by AndroidView with its required session callbacks, never inflated from XML.
@SuppressLint("ViewConstructor")
private class NativeOnboardingScene(
    context: Context,
    private val composition: CompositionContext,
    private val content: State<@Composable (OnboardingStep, Boolean) -> Unit>,
    private val chromeBackdrop: Backdrop,
    private val onNext: () -> Unit,
    private val onBack: () -> Unit,
    private val onSettled: (Long, OnboardingStep) -> Unit,
    private val onGeometry: (Offset, Rect, Size) -> Unit,
) : FrameLayout(context) {
    private val background = OriginalIntroBackground(context)
    private val welcome = createOriginalWelcome(context, ::welcomeNext)
    private val slots = linkedMapOf<OnboardingStep, SceneSlot>()
    private var currentUi = OnboardingUiState()
    private var frame = OnboardingVisualFrame(0f, true, null, OnboardingStep.WELCOME, 0L, 1f, false)
    private var active = false
    private var primaryEnabled = false
    private var backgroundColor = 0
    private var foregroundColor = 0
    private var fontScale = 1f
    private var safeLeft = 0
    private var safeRight = 0
    private var safeTop = 0
    private var safeBottom = 0
    private var center = Offset.Unspecified
    private var arrowBounds = Rect.Zero
    private var measuredCenter = Offset.Unspecified
    private var reportedGeometry: Triple<Offset, Rect, Size>? = null
    private var completedTransition = 0L
    private var released = false
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { measureWelcome() }
    private var scrollObserver: ViewTreeObserver? = null

    private fun welcomeNext() {
        if (active && welcome.next.isEnabled) { measureWelcome(); onNext() }
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(background, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(welcome.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        welcome.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> measureWelcome() }
        welcome.next.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> measureWelcome() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val observer = viewTreeObserver
        scrollObserver = observer
        observer.addOnScrollChangedListener(scrollListener)
    }

    override fun onDetachedFromWindow() {
        clearScrollObserver()
        super.onDetachedFromWindow()
    }

    private fun clearScrollObserver() {
        scrollObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(scrollListener)
        scrollObserver = null
    }

    fun bind(
        ui: OnboardingUiState, frame: OnboardingVisualFrame, active: Boolean, primaryEnabled: Boolean,
        center: Offset, backgroundColor: Int, foregroundColor: Int, fontScale: Float,
        left: Int, top: Int, right: Int, bottom: Int,
    ) {
        if (released) return
        currentUi = ui
        this.frame = frame
        this.active = active
        this.primaryEnabled = primaryEnabled
        this.center = center
        this.backgroundColor = backgroundColor
        this.foregroundColor = foregroundColor
        this.fontScale = fontScale
        safeLeft = left; safeTop = top; safeRight = right; safeBottom = bottom
        val welcomeSide = maxOf(left, right)
        if (welcome.root.paddingLeft != welcomeSide || welcome.root.paddingTop != top ||
            welcome.root.paddingRight != welcomeSide || welcome.root.paddingBottom != bottom) {
            welcome.root.setPadding(welcomeSide, top, welcomeSide, bottom)
        }
        render()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        measureWelcome()
        render()
    }

    private fun measureWelcome() {
        if (released || width <= 0 || height <= 0 || welcome.next.width <= 0) return
        val bounds = layoutBounds(welcome.next)
        arrowBounds = Rect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        val logoBounds = layoutBounds(welcome.logo)
        measuredCenter = Offset(logoBounds.exactCenterX(), logoBounds.exactCenterY())
        val geometry = Triple(measuredCenter, arrowBounds, Size(width.toFloat(), height.toFloat()))
        if (geometry != reportedGeometry) {
            reportedGeometry = geometry
            onGeometry(geometry.first, geometry.second, geometry.third)
        }
    }

    /** Layout/scroll coordinates deliberately exclude entrance scale and alpha transforms. */
    private fun layoutBounds(view: View): AndroidRect {
        var child = view
        var left = 0
        var top = 0
        while (child !== this) {
            val parent = child.parent as? View ?: break
            left += child.left - parent.scrollX
            top += child.top - parent.scrollY
            child = parent
        }
        return AndroidRect(left, top, left + view.width, top + view.height)
    }

    private fun render() {
        if (released) return
        val isRevealPair = frame.fromStep != null &&
            setOf(frame.fromStep, frame.targetStep) == setOf(OnboardingStep.WELCOME, OnboardingStep.STATEMENT)
        val visible = if (frame.transitioning && frame.fromStep != null)
            listOf(frame.fromStep!!, frame.targetStep) else listOf(frame.targetStep)
        val needsWelcome = OnboardingStep.WELCOME in visible
        val glow = needsWelcome || OnboardingStep.COMPLETE in visible
        setBackgroundColor(backgroundColor)
        background.visibility = if (glow) View.VISIBLE else View.INVISIBLE
        background.bind(frame, PointF(if (center.x.isFinite()) center.x else measuredCenter.x,
            if (center.y.isFinite()) center.y else measuredCenter.y))
        background.setActive(active && glow)
        welcome.root.visibility = if (needsWelcome) View.VISIBLE else View.INVISIBLE
        val entranceAlpha = frame.logoAlpha
        val entranceScale = frame.logoScale
        listOf(welcome.logo, welcome.title.parent as View).forEach {
            it.alpha = entranceAlpha
            it.scaleX = entranceScale; it.scaleY = entranceScale
        }
        welcome.next.alpha = OriginalMotionCurves.arrowAlpha(frame.introMillis)
        welcome.next.scaleX = OriginalMotionCurves.arrowScale(frame.introMillis)
        welcome.next.scaleY = OriginalMotionCurves.arrowScale(frame.introMillis)
        val welcomeEnabled = active && !frame.transitioning && !currentUi.transitioning && !frame.showAdmission &&
            frame.introMillis >= OriginalMotionCurves.INTRO_DURATION && currentUi.canContinue && primaryEnabled &&
            frame.targetStep == OnboardingStep.WELCOME
        welcome.next.isEnabled = welcomeEnabled
        welcome.next.isClickable = welcomeEnabled
        welcome.next.isFocusable = welcomeEnabled
        welcome.root.importantForAccessibility = if (welcomeEnabled) IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        val retainedSteps = visible.filter { it != OnboardingStep.WELCOME }
        slots.keys.toList().filter { it !in retainedSteps }.forEach { key ->
            slots.remove(key)?.let { removeView(it.root); it.dispose() }
        }
        retainedSteps.forEach { step ->
            val slot = slots.getOrPut(step) { createSlot(step).also { addView(it.root,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) } }
            val enabled = active && step == frame.targetStep && !frame.transitioning && !currentUi.transitioning &&
                !currentUi.saving && !currentUi.modalOpen
            slot.enabled.value = enabled
            slot.backColor.intValue = if (step == OnboardingStep.COMPLETE) android.graphics.Color.WHITE else foregroundColor
            slot.root.setBackgroundColor(if (step == OnboardingStep.COMPLETE) android.graphics.Color.TRANSPARENT else backgroundColor)
            if (slot.body.paddingLeft != safeLeft || slot.body.paddingTop != safeTop + px(48) ||
                slot.body.paddingRight != safeRight || slot.body.paddingBottom != safeBottom + px(30)) {
                slot.body.setPadding(safeLeft, safeTop + px(48), safeRight, safeBottom + px(30))
            }
            slot.back.isEnabled = active && !currentUi.saving && !currentUi.transitioning && !frame.transitioning
            slot.back.isClickable = step == OnboardingStep.COMPLETE && slot.back.isEnabled
            slot.back.setPadding(0, 0, 0, 0)
            (slot.back.layoutParams as LayoutParams).apply {
                val shadowInset = if (step == OnboardingStep.COMPLETE) 0 else px(OnboardingBackShadowInsetDp)
                val nextTop = safeTop + px(8) - shadowInset
                val nextLeft = safeLeft + px(20) - shadowInset
                if (topMargin != nextTop || leftMargin != nextLeft) {
                    topMargin = nextTop; leftMargin = nextLeft
                    slot.back.layoutParams = this
                }
            }
            slot.footer.isEnabled = enabled && currentUi.canContinue && primaryEnabled
            slot.footer.isClickable = slot.footer is TextView && slot.footer.isEnabled
            slot.primaryEnabled.value = slot.footer.isEnabled
            slot.backEnabled.value = slot.back.isEnabled
            val footerText = context.getString(when {
                currentUi.saving && step == frame.targetStep -> R.string.oobe_saving
                step == OnboardingStep.COMPLETE -> R.string.oobe_start
                else -> R.string.oobe_next
            })
            slot.primaryText.value = footerText
            (slot.footer as? TextView)?.let { footer ->
                footer.alpha = if (footer.isEnabled) 1f else 0.5f
                if (footer.text.toString() != footerText) footer.text = footerText
                val desiredSize = 17f * fontScale / resources.configuration.fontScale.coerceAtLeast(0.1f)
                val sizePixels = desiredSize * resources.displayMetrics.density * resources.configuration.fontScale
                if (kotlin.math.abs(footer.textSize - sizePixels) > 0.01f) footer.textSize = desiredSize
                val desiredHeight = px(maxOf(50f, 22f * fontScale + 28f))
                if (footer.minHeight != desiredHeight) footer.minHeight = desiredHeight
            }
            slot.root.translationX = 0f; slot.root.translationY = 0f; slot.root.alpha = 1f
            if (frame.transitioning && isRevealPair) {
                val geometry = revealGeometry(arrowBounds, Size(width.toFloat(), height.toFloat()))
                val progress = if (frame.targetStep == OnboardingStep.STATEMENT) frame.transitionProgress else 1f - frame.transitionProgress
                slot.root.bindReveal(geometry, progress, true)
            } else {
                slot.root.clearReveal()
                if (frame.transitioning) {
                    val incoming = step == frame.targetStep
                    val direction = if (frame.targetStep.ordinal > (frame.fromStep?.ordinal ?: 0)) 1f else -1f
                    val p = frame.transitionProgress
                    slot.root.translationX = width * direction * if (incoming) (1f - p) else -p
                    slot.root.alpha = if (incoming) p else 1f - p
                }
            }
            slot.root.importantForAccessibility = if (enabled) IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (!frame.transitioning && frame.transitionId > 0L && completedTransition != frame.transitionId && active) {
            // The final scene is already bound; no outgoing composition can unlock another ticket.
            completedTransition = frame.transitionId
            onSettled(frame.transitionId, frame.targetStep)
        }
    }

    private fun createSlot(step: OnboardingStep): SceneSlot {
        val root = CircularRevealFrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        val enabled = mutableStateOf(false)
        val primaryEnabled = mutableStateOf(false)
        val primaryText = mutableStateOf("")
        val backEnabled = mutableStateOf(false)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val compose = ComposeView(context).apply {
            setParentCompositionContext(composition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalPageEffectsActive provides enabled.value) {
                    OriginalOnboardingBody(step, enabled.value) { content.value(step, enabled.value) }
                }
            }
        }
        body.addView(compose, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val footer: View = if (step == OnboardingStep.COMPLETE) TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = ContextCompat.getDrawable(context, R.drawable.oobe_original_primary_button)
            setPadding(px(18), px(12), px(18), px(12))
            setOnClickListener { if (isEnabled && active && frame.targetStep == step) onNext() }
        } else ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
            setParentCompositionContext(composition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OnboardingGlassPrimary(primaryText.value, primaryEnabled.value, chromeBackdrop) {
                    if (primaryEnabled.value && active && frame.targetStep == step) onNext()
                }
            }
        }
        body.addView(footer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = px(30); rightMargin = px(30); topMargin = px(12); bottomMargin = px(24)
        })
        root.addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val back = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            if (step == OnboardingStep.COMPLETE) {
                contentDescription = context.getString(R.string.action_back)
                isFocusable = true
                setOnClickListener { if (isEnabled && active) onBack() }
            }
        }
        val backColor = mutableIntStateOf(foregroundColor)
        val backIcon = ComposeView(context).apply {
            clipChildren = false
            clipToPadding = false
            setParentCompositionContext(composition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            if (step == OnboardingStep.COMPLETE) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                if (step == OnboardingStep.COMPLETE) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(AppIcons.ArrowBack, null, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color(backColor.intValue))
                    }
                } else OnboardingGlassBack(backEnabled.value, chromeBackdrop) {
                    if (backEnabled.value && active && frame.targetStep == step) onBack()
                }
            }
        }
        back.addView(backIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val shadowInset = if (step == OnboardingStep.COMPLETE) 0 else px(OnboardingBackShadowInsetDp)
        val backHostSize = px(48) + 2 * shadowInset
        root.addView(back, LayoutParams(backHostSize, backHostSize, Gravity.TOP or Gravity.START))
        return SceneSlot(root, body, compose, footer, back, backIcon, backColor, enabled,
            primaryEnabled, primaryText, backEnabled)
    }

    fun release() {
        if (released) return
        released = true
        clearScrollObserver()
        background.release()
        slots.values.forEach { it.dispose() }
        slots.clear()
        removeAllViews()
    }

    private fun px(value: Int) = px(value.toFloat())
    private fun px(value: Float) = (value * resources.displayMetrics.density).toInt()

    private data class SceneSlot(
        val root: CircularRevealFrameLayout, val body: LinearLayout, val compose: ComposeView,
        val footer: View, val back: FrameLayout, val backIcon: ComposeView,
        val backColor: MutableIntState, val enabled: MutableState<Boolean>,
        val primaryEnabled: MutableState<Boolean>, val primaryText: MutableState<String>,
        val backEnabled: MutableState<Boolean>,
    ) { fun dispose() { compose.disposeComposition(); backIcon.disposeComposition(); (footer as? ComposeView)?.disposeComposition() } }
}
