/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import com.risediary.app.ui.navigation3.Route
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

/**
 * Timing, background treatment and predictive velocity filtering adapted from
 * HyperIsland a045afeb (Copyright (c) 2026 1812z, MIT).
 * The upstream notice is retained in LICENSES/HyperIsland-MIT.txt and APK assets.
 *
 * miuix-nav remains the sole owner of the animation clock, gestures, entry state
 * and ViewModels. This class maps that clock onto the reference's visual tracks.
 */
internal data class NavigationFrame(
    val translationXFraction: Float,
    val scale: Float,
    val blurIntensity: Float,
)

private data class BoundaryPose(
    val front: Float,
    val depth: Float,
    val blur: Float,
) {
    fun toward(target: BoundaryPose, fraction: Float): BoundaryPose {
        val t = fraction.coerceIn(0f, 1f)
        return BoundaryPose(
            front + (target.front - front) * t,
            depth + (target.depth - depth) * t,
            blur + (target.blur - blur) * t,
        )
    }
}

private object TransitionTiming {
    const val Enter = 420
    const val Exit = 380
    const val Cancel = 280
    const val MinCommit = 24
    const val MaxCommit = 480
    const val BackgroundParallax = 0.025f
}

private enum class VisualPhase { Rest, Programmatic, Gesture, Cancel, Commit }

internal class HyperIslandNavigationMotion(
    initialStack: List<Route>,
    private val timeNanos: () -> Long = System::nanoTime,
) {
    private data class Registration(val scope: NavTransitionScope, val index: Int)
    private val scopes = mutableStateMapOf<Route, Registration>()
    private var keys = initialStack.toList()
    private var previousKeys = keys
    private var session = Session(
        route = keys.last(),
        index = keys.lastIndex,
        initial = BoundaryPose(0f, 1f, 1f),
        rawStart = 0f,
        target = BoundaryPose(0f, 1f, 1f),
        fromRest = true,
    )
    private val velocity = VisualVelocityTracker(timeNanos)
    private var transition = createTransition(TransitionTiming.Enter, null)

    private class Session(
        val route: Route,
        val index: Int,
        val initial: BoundaryPose,
        val rawStart: Float,
        val target: BoundaryPose,
        val fromRest: Boolean = false,
        val commit: Boolean = false,
    ) {
        var reference: NavTransitionScope? = null
        var ignoredGesture: NavGesture? = null
        var commitGesture: NavGesture? = null
        var pendingHandoff = false
        var gestureInputStart = 0f
        var phase = if (commit) VisualPhase.Commit else VisualPhase.Rest
        var phaseClock: NavSettle? = null
        var phaseStart = initial
        var phaseRaw = rawStart
        var gestureStart = initial
        var gestureRaw = rawStart
        var last = initial
        var lastRaw = rawStart
        var lastSampleRaw = Float.NaN
        var lastSampleGesture = Float.NaN
        var lastSampleClock: NavSettle? = null
    }

    /** Called only when the stack changes, before NavDisplay resolves its motion. */
    fun updateStack(stack: List<Route>): NavTransition {
        if (stack == keys) return transition
        require(stack.isNotEmpty())
        val old = session
        val oldScope = referenceScope()
        val oldPose = oldScope?.let { boundary(it) } ?: old.last
        val oldKeys = keys
        previousKeys = oldKeys
        keys = stack.toList()
        val popping = keys.size < oldKeys.size
        // A second pop may arrive while an already removed page is still visible.
        // Keep that foremost boundary until the native host actually culls it.
        val keepLeaving = popping && old.index > oldKeys.lastIndex &&
            old.target.front == 1f && oldScope != null &&
            oldScope.relativeDepth > -1f && oldScope.relativeDepth <= 0f
        val movingRoute = if (keepLeaving) old.route else if (popping) oldKeys.last() else keys.last()
        val movingIndex = if (keepLeaving) old.index else if (popping) oldKeys.lastIndex else keys.lastIndex
        val movingScope = scopes[movingRoute]?.scope
            ?: old.reference.takeIf { movingRoute == old.route }
        val raw = movingScope?.let { (-it.relativeDepth).coerceIn(0f, 1f) } ?: 1f
        val sameBoundary = movingRoute == old.route
        val predictive = popping && movingScope?.gesture != null
        val initial = when {
            sameBoundary -> oldPose
            // The previous upper page may have finished leaving. A newly exposed
            // pop boundary starts at its own live depth, not an offscreen enter pose.
            popping -> BoundaryPose(raw, 1f - raw, 1f - raw)
            else -> BoundaryPose(1f, 0f, 0f)
        }
        val target = if (popping) BoundaryPose(1f, 0f, 0f)
            else BoundaryPose(0f, 1f, 1f)
        session = Session(
            movingRoute, movingIndex, initial, raw, target,
            commit = predictive,
        ).also {
            it.reference = movingScope
            if (predictive) it.commitGesture = movingScope.gesture
            else it.ignoredGesture = movingScope?.gesture
        }
        transition = createTransition(
            if (popping) TransitionTiming.Exit else TransitionTiming.Enter, if (predictive) initial else null,
            rawStart = raw, targetDistance = movingIndex - keys.lastIndex,
            durationFront = if (keepLeaving) scopes[oldKeys.last()]?.scope?.let {
                (-it.relativeDepth).coerceIn(0f, 1f)
            } ?: 0f else initial.front,
        )
        return transition
    }

    fun register(route: Route, scope: NavTransitionScope) {
        val index = keys.indexOf(route).takeIf { it >= 0 }
            ?: previousKeys.indexOf(route).takeIf { it >= 0 }
            ?: session.index.takeIf { route == session.route } ?: scopes[route]?.index ?: return
        scopes[route] = Registration(scope, index)
        if (route == session.route) session.reference = scope
    }

    fun unregister(route: Route, scope: NavTransitionScope) {
        if (scopes[route]?.scope === scope) scopes.remove(route)
        // Retain one live driver reference across culling/scope replacement.
        // It is dropped with the next session, never a list of old entry scopes.
    }

    val rootReturnInProgress: Boolean
        get() {
            if (keys.size != 1 || session.index == 0) return false
            val outgoing = referenceScope()
            val driver = outgoing ?: scopes[Route.Main]?.scope ?: return true
            val rootDepth = if (outgoing != null) {
                outgoing.relativeDepth + session.index
            } else driver.relativeDepth
            // A predictive preview can reach the native end position before
            // its visual exit and gesture cleanup have completed.
            return rootDepth > 0f || driver.settle != null || driver.gesture != null
        }

    /** Start the independent bar animation when back targets Main, not during preview. */
    val rootBarVisible: Boolean
        get() = keys.size == 1 && keys[0] == Route.Main

    /** Root interactions are available only after all outgoing entries have left. */
    val rootPageReady: Boolean
        get() = keys.size == 1 && keys[0] == Route.Main && !rootReturnInProgress

    private fun referenceScope(): NavTransitionScope? =
        scopes[session.route]?.scope ?: session.reference

    private fun sourceDepth(scope: NavTransitionScope): Float {
        referenceScope()?.let { return it.relativeDepth }
        val index = scopes.values.firstOrNull { it.scope === scope }?.index
            ?: if (scope.relativeDepth > 0f) session.index - 1 else session.index
        return scope.relativeDepth - (session.index - index)
    }

    private fun boundary(scope: NavTransitionScope): BoundaryPose {
        val state = session
        val raw = (-sourceDepth(scope)).coerceIn(0f, 1f)
        val clock = scope.settle
        val gesture = scope.gesture.takeUnless { it === state.ignoredGesture }
        val finger = gesture?.progress?.coerceIn(0f, 1f) ?: -1f
        if (raw == state.lastSampleRaw && finger == state.lastSampleGesture &&
            clock === state.lastSampleClock
        ) return state.last

        if (state.commit && clock == null && gesture != null &&
            gesture !== state.commitGesture && state.route != keys.last()
        ) {
            if (!state.pendingHandoff) {
                val offset = state.index - keys.lastIndex
                val anchor = if (finger < 0.999f) {
                    offset + (raw - offset - finger) / (1f - finger)
                } else raw
                state.phaseRaw = anchor.coerceIn(0f, 1f)
                state.phaseStart = state.initial.toward(
                    state.target, intervalProgress(state.phaseRaw, state.rawStart, 1f),
                )
                state.pendingHandoff = true
            }
            if (sourceDepth(scope) <= -1f) {
                // The older exit has left the screen. Continue the same gesture
                // from the newly exposed page rather than replaying its beginning.
                val current = scopes[keys.last()] ?: return state.last
                val anchor = (-current.scope.relativeDepth).coerceIn(0f, 1f)
                val pose = BoundaryPose(anchor, 1f - anchor, 1f - anchor)
                session = Session(
                    keys.last(), keys.lastIndex, pose, anchor,
                    BoundaryPose(0f, 1f, 1f),
                ).also {
                    it.reference = current.scope
                    it.phase = VisualPhase.Gesture
                    it.gestureStart = pose
                    it.gestureRaw = anchor
                    it.gestureInputStart = finger
                }
                velocity.reset(anchor)
                return boundary(scope)
            }
        }

        val next = when {
            state.pendingHandoff && clock == null && gesture != null -> {
                state.phaseStart.toward(
                    state.target, intervalProgress(raw, state.phaseRaw, 1f),
                )
            }
            state.commit -> {
                val fraction = intervalProgress(raw, state.rawStart, 1f)
                state.initial.toward(state.target, fraction)
            }
            clock?.phase == NavSettlePhase.Cancel -> {
                if (state.phase != VisualPhase.Cancel || state.phaseClock !== clock) {
                    state.phaseStart = if (state.phase == VisualPhase.Gesture && gesture != null) {
                        preview(state, finger)
                    } else state.last
                    state.phaseRaw = if (state.phase == VisualPhase.Gesture && gesture != null) {
                        state.gestureRaw + (1f - state.gestureRaw) * gestureProgress(state, finger)
                    } else state.lastRaw
                    state.phase = VisualPhase.Cancel
                    state.phaseClock = clock
                }
                state.phaseStart.toward(
                    BoundaryPose(0f, 1f, 1f),
                    intervalProgress(raw, state.phaseRaw, 0f),
                )
            }
            gesture != null && clock == null -> {
                if (state.phase != VisualPhase.Gesture) {
                    state.gestureRaw = if (finger < 0.999f) {
                        ((raw - finger) / (1f - finger)).coerceIn(0f, 1f)
                    } else raw
                    state.gestureStart = if (state.phase == VisualPhase.Rest ||
                        state.phase == VisualPhase.Programmatic
                    ) {
                        val anchor = state.gestureRaw
                        val pose = if (state.fromRest) {
                            BoundaryPose(anchor, 1f - anchor, 1f - anchor)
                        } else {
                            state.initial.toward(
                                state.target, intervalProgress(anchor, state.rawStart, state.target.front),
                            )
                        }
                        pose
                    } else state.last
                    state.gestureInputStart = 0f
                    state.phase = VisualPhase.Gesture
                    state.phaseClock = null
                    velocity.reset(state.gestureStart.front)
                }
                preview(state, finger)
            }
            else -> {
                state.phase = if (clock != null) VisualPhase.Programmatic else VisualPhase.Rest
                state.phaseClock = clock
                val pose = if (state.fromRest) {
                    BoundaryPose(raw, 1f - raw, 1f - raw)
                } else {
                    state.initial.toward(state.target, intervalProgress(raw, state.rawStart, state.target.front))
                }
                pose
            }
        }
        velocity.update(next.front)
        state.last = next
        state.lastRaw = raw
        state.lastSampleRaw = raw
        state.lastSampleGesture = finger
        state.lastSampleClock = clock
        return next
    }

    private fun gestureProgress(state: Session, progress: Float): Float =
        intervalProgress(progress, state.gestureInputStart, 1f)

    private fun preview(state: Session, progress: Float): BoundaryPose {
        val p = gestureProgress(state, progress)
        val smooth = smootherStep(p)
        val start = state.gestureStart
        return BoundaryPose(
            start.front + (1f - start.front) * 0.5f * p,
            start.depth * (1f - smooth),
            start.blur * (1f - 0.5f * smooth),
        )
    }

    /** Deferred by graphicsLayer/drawBackdrop; content does not subscribe per frame. */
    fun frame(scope: NavTransitionScope): NavigationFrame {
        val pose = boundary(scope)
        val d = scope.relativeDepth
        val difference = d - sourceDepth(scope)
        return when {
            abs(difference) < 0.01f -> NavigationFrame(pose.front, 1f, 0f)
            abs(difference - 1f) < 0.01f && d >= 0f ->
                NavigationFrame(-TransitionTiming.BackgroundParallax * pose.depth, 1f, pose.blur)
            d <= 0f -> NavigationFrame((-d).coerceIn(0f, 1f), 1f, 0f)
            else -> {
                val depth = d.coerceIn(0f, 1f)
                NavigationFrame(-TransitionTiming.BackgroundParallax * depth, 1f, depth)
            }
        }
    }

    private fun createTransition(
        duration: Int,
        commitStart: BoundaryPose?,
        rawStart: Float = 0f,
        targetDistance: Int = 1,
        durationFront: Float = commitStart?.front ?: 0f,
    ): NavTransition {
        val commitDuration = if (commitStart != null) {
            (TransitionTiming.MaxCommit * (1f - durationFront)).roundToInt().coerceIn(TransitionTiming.MinCommit, TransitionTiming.MaxCommit)
        } else TransitionTiming.MaxCommit
        val remaining = (1f - (commitStart?.front ?: 0f)).coerceAtLeast(0.0005f)
        val renderGain = remaining / (1f - rawStart).coerceAtLeast(0.0005f)
        val nativeDistance = (targetDistance - rawStart).coerceAtLeast(0.0005f)
        val slope = velocity.releaseVelocity() * (commitDuration / 1000f) / (renderGain * nativeDistance)
        val easing = CubicBezierEasing(0.30f, (0.30f * slope).coerceIn(0f, 0.42f), 0.35f, 1f)
        val motion = NavMotion(
            programmatic = NavSettleSpec.Tween(duration, FastOutSlowInEasing),
            cancel = NavSettleSpec.Tween(TransitionTiming.Cancel, FastOutSlowInEasing),
            commit = NavSettleSpec.Tween(commitDuration, easing),
        )
        return object : NavTransition {
            override val motion = motion
            override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = graphicsLayer {
                val visual = frame(scope)
                val sign = if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
                translationX = (sign * size.width * visual.translationXFraction).roundToInt().toFloat()
                scaleX = visual.scale
                scaleY = visual.scale
            }
            override fun scrimFraction(scope: NavTransitionScope): Float = frame(scope).blurIntensity
        }
    }
}

private fun intervalProgress(value: Float, start: Float, end: Float): Float =
    if (abs(end - start) < 0.00001f) 1f else ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun smootherStep(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

/** Reference filtering, measured in rendered screen-widths/second. */
private class VisualVelocityTracker(private val now: () -> Long) {
    private var last = 0f
    private var sampled = Long.MIN_VALUE
    private var moved = Long.MIN_VALUE
    private var filtered = 0f
    fun reset(position: Float) {
        last = position
        sampled = now()
        moved = sampled
        filtered = 0f
    }
    fun update(position: Float) {
        if (position == last) return
        val time = now()
        val elapsed = time - sampled
        val delta = position - last
        if (elapsed in 1..120_000_000L && abs(delta) >= 0.00025f) {
            if (delta > 0f) {
                val seconds = elapsed / 1_000_000_000f
                val blend = (seconds / 0.05f).coerceIn(0.2f, 0.65f)
                filtered += ((delta / seconds).coerceIn(0f, 3f) - filtered) * blend
            } else filtered = 0f
            moved = time
        }
        last = position
        sampled = time
    }
    fun releaseVelocity(): Float {
        if (moved == Long.MIN_VALUE) return 0f
        val idle = (now() - moved).coerceAtLeast(0L)
        val retain = when {
            idle <= 40_000_000L -> 1f
            idle >= 160_000_000L -> 0f
            else -> smootherStep((160_000_000L - idle) / 120_000_000f)
        }
        return filtered * retain
    }
}
