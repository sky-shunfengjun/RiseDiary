/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.risediary.app.ui.navigation3.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettle
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeEdge
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

class HyperIslandNavigationMotionTest {
    private class Driver {
        var top by mutableFloatStateOf(1f)
        var gesture by mutableStateOf<NavGesture?>(null)
        var settle by mutableStateOf<NavSettle?>(null)
        var change: NavChange = NavChange.None
        var nanos = 0L
    }
    private class Scope(val driver: Driver, val index: Int) : NavTransitionScope {
        override val relativeDepth get() = driver.top - index
        override val gesture get() = driver.gesture
        override val settle get() = driver.settle
        override val change get() = driver.change
        override val role get() = if (relativeDepth > 0f) NavRole.Covered else NavRole.Top
        override val layoutSize = IntSize(1080, 2400)
        override val layoutDirection = LayoutDirection.Ltr
        override val density = Density(3f)
    }
    private class Settle(
        override val phase: NavSettlePhase,
        override val releaseVelocity: Float = 0f,
        override var elapsedMillis: Float = 0f,
    ) : NavSettle
    private class Harness {
        val driver = Driver()
        val root = Scope(driver, 0)
        val page = Scope(driver, 1)
        val motion = HyperIslandNavigationMotion(listOf(Route.Main, Route.About)) { driver.nanos }
        init {
            motion.register(Route.Main, root)
            motion.register(Route.About, page)
        }
        fun drag(progress: Float) {
            driver.nanos += 16_000_000L
            driver.top = 1f - progress
            driver.settle = null
            driver.gesture = NavGesture(progress, NavSwipeEdge.Left, 500f, 500f)
        }
        fun release(phase: NavSettlePhase): Settle {
            val settle = Settle(phase)
            driver.settle = settle
            return settle
        }
    }

    @Test fun fullGestureShowsHalfScreenAndRetainsHalfBlur() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.999f)
        val front = h.motion.frame(h.page)
        val back = h.motion.frame(h.root)
        assertEquals(0.5f, front.translationXFraction, 0.001f)
        assertEquals(0.5f, back.blurIntensity, 0.001f)
        assertEquals(1f, back.scale, 0.001f)
    }

    @Test fun nestedPageKeepsItsSizeWithTheSameCoveredBlurAsHome() {
        val h = Harness()
        val third = Scope(h.driver, 2)
        h.motion.updateStack(listOf(Route.Main, Route.About, Route.ThirdPartyLibs))
        h.motion.register(Route.ThirdPartyLibs, third)
        h.driver.top = 2f
        h.drag(0f)
        h.driver.top = 2f
        h.motion.frame(third)
        h.drag(0.5f)
        h.driver.top = 1.5f
        assertEquals(0.25f, h.motion.frame(third).translationXFraction, 0.0001f)
        val revealed = h.motion.frame(h.page)
        assertEquals(1f, revealed.scale, 0.0001f)
        assertEquals(-0.0125f, revealed.translationXFraction, 0.0001f)
        assertEquals(0.75f, revealed.blurIntensity, 0.0001f)
        assertTrue(!h.motion.rootPageReady)
    }

    @Test fun commitStartsAtPreviewPoseAndFinishesOffscreen() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.3f)
        val beforeFront = h.motion.frame(h.page)
        val beforeBack = h.motion.frame(h.root)
        val transition = h.motion.updateStack(listOf(Route.Main))
        h.driver.change = NavChange.Pop
        h.release(NavSettlePhase.Commit)
        assertEquals(beforeFront.translationXFraction, h.motion.frame(h.page).translationXFraction, 0.0001f)
        assertEquals(beforeBack.blurIntensity, h.motion.frame(h.root).blurIntensity, 0.0001f)
        assertTrue(transition.motion.commit is NavSettleSpec.Tween)
        h.driver.top = 0f
        assertEquals(1f, h.motion.frame(h.page).translationXFraction, 0.0001f)
        assertEquals(0f, h.motion.frame(h.root).blurIntensity, 0.0001f)
        assertEquals(1f, h.motion.frame(h.root).scale, 0.0001f)
    }

    @Test fun cancelDoesNotJumpAndRestoresTheCoveredPage() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.5f)
        val front = h.motion.frame(h.page)
        val back = h.motion.frame(h.root)
        h.release(NavSettlePhase.Cancel)
        assertEquals(front.translationXFraction, h.motion.frame(h.page).translationXFraction, 0.0001f)
        assertEquals(back.blurIntensity, h.motion.frame(h.root).blurIntensity, 0.0001f)
        h.driver.top = 0.75f
        assertEquals(0.125f, h.motion.frame(h.page).translationXFraction, 0.0001f)
        h.driver.top = 1f
        assertEquals(0f, h.motion.frame(h.page).translationXFraction, 0.0001f)
        assertEquals(1f, h.motion.frame(h.root).blurIntensity, 0.0001f)
        assertEquals(1f, h.motion.frame(h.root).scale, 0.0001f)
    }

    @Test fun grabbingCancelKeepsTheAlreadyRenderedPose() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.6f)
        h.motion.frame(h.page)
        h.release(NavSettlePhase.Cancel)
        h.motion.frame(h.page)
        h.driver.top = 0.6f
        val current = h.motion.frame(h.page)
        h.driver.settle = null
        h.driver.gesture = NavGesture(0f, NavSwipeEdge.Left, 500f, 500f)
        assertEquals(current.translationXFraction, h.motion.frame(h.page).translationXFraction, 0.0001f)
        assertEquals(0.2f, current.translationXFraction, 0.0001f)
    }

    @Test fun replacingEntryScopeDoesNotRestartThePreview() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.4f)
        val before = h.motion.frame(h.page)
        val replacement = Scope(h.driver, 1)
        h.motion.unregister(Route.About, h.page)
        h.motion.register(Route.About, replacement)
        assertEquals(before, h.motion.frame(replacement))
        assertEquals(0.2f, before.translationXFraction, 0.0001f)
    }

    @Test fun previewAndCancelKeepTheBarHiddenUntilReturnConfirmation() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.5f)
        assertTrue(!h.motion.rootBarVisible)
        assertTrue(!h.motion.rootPageReady)
        h.drag(1f)
        assertTrue(!h.motion.rootBarVisible)
        assertTrue(!h.motion.rootPageReady)
        h.release(NavSettlePhase.Cancel)
        assertTrue(!h.motion.rootBarVisible)
        h.driver.top = 1f
        h.driver.gesture = null
        h.driver.settle = null
        assertTrue(!h.motion.rootPageReady)

        assertTrue(!h.motion.rootBarVisible)
        h.drag(0.6f)
        h.motion.frame(h.page)
        assertTrue(!h.motion.rootBarVisible)
        h.motion.updateStack(listOf(Route.Main))
        h.release(NavSettlePhase.Commit)
        assertTrue(h.motion.rootBarVisible) // reveal starts while the outgoing page still settles
        assertTrue(!h.motion.rootPageReady) // click protection lasts until the exit completes
        h.driver.top = 0f
        assertTrue(!h.motion.rootPageReady) // end position alone is not settle completion
        h.driver.settle = null
        assertTrue(!h.motion.rootPageReady) // native gesture cleanup is still pending
        h.driver.gesture = null
        assertTrue(h.motion.rootPageReady)
    }

    @Test fun programmaticReturnRevealsBarWhileRootInteractionsWait() {
        val driver = Driver().apply { top = 0f }
        val motion = HyperIslandNavigationMotion(listOf(Route.Main))
        val root = Scope(driver, 0)
        val page = Scope(driver, 1)
        motion.register(Route.Main, root)
        motion.updateStack(listOf(Route.Main, Route.About))
        motion.register(Route.About, page)
        driver.settle = Settle(NavSettlePhase.Programmatic)
        driver.top = 0.5f
        assertTrue(!motion.rootBarVisible)
        motion.updateStack(listOf(Route.Main))
        assertTrue(motion.rootBarVisible)
        assertTrue(!motion.rootPageReady)
        driver.top = 0.05f
        assertTrue(!motion.rootPageReady)
        driver.top = 0f
        assertTrue(!motion.rootPageReady)
        driver.settle = null
        assertTrue(motion.rootPageReady)
    }

    @Test fun restoredSecondaryStackKeepsBarHiddenWithoutAScope() {
        val motion = HyperIslandNavigationMotion(listOf(Route.Main, Route.RecordDetail(42)))
        assertTrue(!motion.rootBarVisible)
        assertTrue(!motion.rootPageReady)
        motion.updateStack(listOf(Route.Main, Route.RecordDetail(42), Route.RecordEdit(42)))
        assertTrue(!motion.rootBarVisible)
        assertTrue(!motion.rootPageReady)
    }

    @Test fun rootInteractionReadinessSurvivesTheMainEntryBeingCulled() {
        val h = Harness()
        h.motion.unregister(Route.Main, h.root)
        h.motion.updateStack(listOf(Route.Main))
        h.release(NavSettlePhase.Programmatic)
        h.driver.top = 0.4f
        assertTrue(!h.motion.rootPageReady)
        h.driver.top = 0f
        assertTrue(!h.motion.rootPageReady)
        h.driver.settle = null
        assertTrue(h.motion.rootPageReady)
    }

    @Test fun multiLevelReturnRevealsBarOnlyWhenTheStackTargetsRoot() {
        val h = Harness()
        val third = Scope(h.driver, 2)
        h.motion.updateStack(listOf(Route.Main, Route.About, Route.ThirdPartyLibs))
        h.motion.register(Route.ThirdPartyLibs, third)
        h.driver.top = 2f
        h.motion.frame(third)
        h.motion.updateStack(listOf(Route.Main, Route.About))
        assertTrue(!h.motion.rootBarVisible)
        h.motion.updateStack(listOf(Route.Main))
        h.driver.settle = Settle(NavSettlePhase.Programmatic)
        h.driver.top = 0.5f
        assertTrue(h.motion.rootBarVisible)
        assertTrue(!h.motion.rootPageReady)
        h.driver.top = 0f
        assertTrue(!h.motion.rootPageReady)
        h.driver.settle = null
        assertTrue(h.motion.rootPageReady)
    }

    @Test fun secondBackDuringCommitKeepsTheVisibleOutgoingPagePosition() {
        val h = Harness()
        val third = Scope(h.driver, 2)
        h.motion.updateStack(listOf(Route.Main, Route.About, Route.ThirdPartyLibs))
        h.motion.register(Route.ThirdPartyLibs, third)
        h.driver.top = 2f
        h.motion.frame(third)
        h.drag(0f)
        h.driver.top = 2f
        h.motion.frame(third)
        h.drag(0.3f)
        h.driver.top = 1.7f
        h.motion.frame(third)
        h.motion.updateStack(listOf(Route.Main, Route.About))
        h.release(NavSettlePhase.Commit)
        h.driver.top = 1.55f
        val before = h.motion.frame(third)
        h.motion.updateStack(listOf(Route.Main))
        h.release(NavSettlePhase.Commit)
        assertEquals(before.translationXFraction, h.motion.frame(third).translationXFraction, 0.0001f)
    }

    @Test fun reopeningDuringCommitDoesNotReplayTheFrozenGestureAfterSettling() {
        val h = Harness()
        h.drag(0f)
        h.motion.frame(h.page)
        h.drag(0.4f)
        h.motion.frame(h.page)
        h.motion.updateStack(listOf(Route.Main))
        h.release(NavSettlePhase.Commit)
        h.driver.top = 0.4f
        val before = h.motion.frame(h.page)
        h.motion.updateStack(listOf(Route.Main, Route.About))
        h.release(NavSettlePhase.Programmatic)
        assertEquals(before.translationXFraction, h.motion.frame(h.page).translationXFraction, 0.0001f)
        h.driver.top = 1f
        h.motion.frame(h.page)
        h.driver.settle = null
        assertEquals(0f, h.motion.frame(h.page).translationXFraction, 0.0001f)
    }

    @Test fun newGestureDuringAnOlderExitHandsOffToTheCurrentPageContinuously() {
        val h = Harness()
        val third = Scope(h.driver, 2)
        h.motion.updateStack(listOf(Route.Main, Route.About, Route.ThirdPartyLibs))
        h.motion.register(Route.ThirdPartyLibs, third)
        h.driver.top = 2f
        h.motion.frame(third)
        h.drag(0f)
        h.driver.top = 2f
        h.motion.frame(third)
        h.drag(0.3f)
        h.driver.top = 1.7f
        h.motion.frame(third)
        h.motion.updateStack(listOf(Route.Main, Route.About))
        h.release(NavSettlePhase.Commit)
        h.driver.top = 1.55f
        val before = h.motion.frame(third)
        h.drag(0f)
        h.driver.top = 1.55f
        assertEquals(before.translationXFraction, h.motion.frame(third).translationXFraction, 0.0001f)
        h.drag(0.4f)
        h.driver.top = 0.93f
        assertEquals(0.07f, h.motion.frame(h.page).translationXFraction, 0.0001f)
        h.drag(1f)
        h.driver.top = 0f
        assertEquals(0.535f, h.motion.frame(h.page).translationXFraction, 0.0001f)
        assertEquals(0.465f, h.motion.frame(h.root).blurIntensity, 0.0001f)
    }

    @Test fun pausingBeforeReleaseDoesNotReuseOldFlingMomentum() {
        fun easingAdvance(pause: Boolean): Float {
            val h = Harness()
            h.drag(0f)
            h.motion.frame(h.page)
            h.drag(0.2f)
            h.motion.frame(h.page)
            h.drag(0.6f)
            h.motion.frame(h.page)
            if (pause) h.driver.nanos += 200_000_000L
            val transition = h.motion.updateStack(listOf(Route.Main))
            return (transition.motion.commit as NavSettleSpec.Tween).easing.transform(0.1f)
        }
        assertTrue(easingAdvance(false) > easingAdvance(true) + 0.02f)
    }

    @Test fun rootReturnConsumesExtraBackUntilTheFullStackHasFinishedLeaving() {
        val h = Harness()
        val third = Scope(h.driver, 2)
        h.motion.updateStack(listOf(Route.Main, Route.About, Route.ThirdPartyLibs))
        h.motion.register(Route.ThirdPartyLibs, third)
        h.driver.top = 2f
        h.motion.frame(third)
        h.motion.updateStack(listOf(Route.Main))
        assertTrue(h.motion.rootReturnInProgress)
        h.driver.top = 0.5f
        assertTrue(h.motion.rootReturnInProgress)
        h.driver.top = 0f
        assertTrue(!h.motion.rootReturnInProgress)
    }
}
