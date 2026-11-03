package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.*
import com.risediary.app.ui.theme.RiseDiaryTheme
import com.risediary.app.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryRecommendationRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val video = LibraryAsset("00000000-0000-0000-0000-000000000001", null, "video", "one.mp4", "one.mp4", "video/mp4", 10L, 1000L, "hash", 0L, 1L, "ready")
    private data class TrackingEdgeFactory(val platform: OverscrollFactory, val onCreate: () -> Unit) : OverscrollFactory {
        override fun createOverscrollEffect() = platform.createOverscrollEffect().also { onCreate() }
    }
    @Test fun pullingAtRecommendationTopDoesNotInstallAnEdgeEffectOrExposeABlankBand() {
        var edgeEffects = 0
        lateinit var browserGrid: androidx.compose.foundation.lazy.grid.LazyGridState
        compose.setContent { RiseDiaryTheme {
            val platform = requireNotNull(LocalOverscrollFactory.current)
            val tracking = remember(platform) { TrackingEdgeFactory(platform) { edgeEffects++ } }
            CompositionLocalProvider(LocalOverscrollFactory provides tracking) {
                Box(Modifier.width(320.dp).height(640.dp).testTag("library-root")) {
                    val snapshot = LibrarySnapshot((1..20).map { video.copy(id = "00000000-0000-0000-0000-${it.toString().padStart(12, '0')}") })
                    val state = LibraryBrowserState(snapshot = snapshot, loading = false, recommendation = video,
                        entries = LibraryTree(snapshot).entries(null, "", LibrarySort.NEWEST))
                    LibraryPageLayout(state, false, {}, {}) { grid, header, bottom ->
                        browserGrid = grid
                        LibraryBrowserContent(state, LibraryTransferState(), grid, header, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                            bottomSpacing = bottom, recommendation = { LibraryRecommendationCard(video, 260.dp, true, {}) })
                    }
                }
            }
        } }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeDown() }
        val root = compose.onNodeWithTag("library-root").fetchSemanticsNode().boundsInRoot
        val hero = compose.onNodeWithTag("library-daily-recommendation").fetchSemanticsNode().boundsInRoot
        assertEquals(root.top, hero.top, 1f)
        compose.runOnIdle { assertEquals("The edge-to-edge cover must not be pulled away from the top", 0, edgeEffects) }
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("Normal scrolling must remain available", browserGrid.canScrollBackward) }
    }
    @Test fun shortCoverSheetKeepsSaveReachableWhileNormalLayoutPinsIt() {
        var compact by mutableStateOf(false)
        var saves = 0
        compose.setContent { RiseDiaryTheme {
            LibraryCoverSheetLayout(compact, Modifier.width(320.dp).height(240.dp), actions = {
                RiseDialogActionButton(RiseDialogAction("保存封面", { saves++ }, DialogActionTone.Normal), Modifier.fillMaxWidth())
            }) { Box(Modifier.fillMaxWidth().height(420.dp)) }
        } }
        compose.onNodeWithText("保存封面").assertIsDisplayed().performClick()
        compose.runOnIdle { compact = true }
        compose.onNodeWithText("保存封面").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, saves) }
    }
    @Test fun dailyCardBleedsThroughGridGuttersToTheScreenTopAndOpensTheViewer() {
        var opens = 0
        compose.setContent { RiseDiaryTheme {
            Box(Modifier.width(320.dp).height(640.dp).testTag("library-root")) {
                val state = LibraryBrowserState(snapshot = LibrarySnapshot(listOf(video)), loading = false, recommendation = video)
                LibraryPageLayout(state, false, {}, {}) { grid, header, bottom ->
                    LibraryBrowserContent(state, LibraryTransferState(), grid, header, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                        bottomSpacing = bottom, recommendation = { LibraryRecommendationCard(video, 260.dp, true, { opens++ }) })
                }
            }
        } }
        val root = compose.onNodeWithTag("library-root").fetchSemanticsNode().boundsInRoot
        val hero = compose.onNodeWithTag("library-daily-recommendation").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(root.left, hero.left, 1f)
        assertEquals(root.right, hero.right, 1f)
        assertEquals(root.top, hero.top, 1f)
        compose.onNodeWithContentDescription("观看每日推荐").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }
    @Test fun videoMenuOffersCoverEditing() {
        var cover = false
        compose.setContent { RiseDiaryTheme {
            LibraryBrowserItem(LibraryTree(LibrarySnapshot(listOf(video))).entries(null, "", LibrarySort.NEWEST).single(),
                true, false, false, true, {}, {}, {}, {}, {}, onCover = { cover = true })
        } }
        compose.onNodeWithContentDescription("one.mp4").performTouchInput { longClick() }
        compose.onNodeWithText("修改封面").performClick()
        compose.runOnIdle { assertTrue(cover) }
    }
}
