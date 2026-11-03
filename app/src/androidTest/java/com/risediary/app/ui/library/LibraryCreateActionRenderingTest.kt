package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidGlassBottomBar
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryCreateActionRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun rootCreateActionIsAboveAndCenteredOnFlightAction() {
        compose.setContent {
            RiseDiaryTheme {
                val backdrop = rememberLayerBackdrop()
                val state = LibraryBrowserState(loading = false)
                Box(Modifier.width(360.dp).height(640.dp)) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        LibraryPageLayout(state, false, {}, toolbar = {}, floatingAction = { source ->
                            LibraryCreateButton(source, true, {}, {})
                        }) { _, _, _ -> Box(Modifier.fillMaxSize()) }
                    }
                    LiquidGlassBottomBar(1, {}, {}, backdrop, Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
                }
            }
        }
        val flightLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.bottom_tab_start_flight)
        val flight = compose.onNodeWithContentDescription(flightLabel).fetchSemanticsNode().boundsInRoot
        val create = compose.onNodeWithTag("library-create-action").fetchSemanticsNode().boundsInRoot
        assertEquals(flight.center.x, create.center.x, 1f)
        assertTrue(create.bottom < flight.top)
    }

    @Test fun folderCreateActionIsBottomRightAndToolbarContainsOnlyTwoActions() {
        var inset = 0f
        var toolbarLimit = 0f
        compose.setContent {
            RiseDiaryTheme {
                inset = with(LocalDensity.current) { 20.dp.toPx() }
                toolbarLimit = with(LocalDensity.current) { 132.dp.toPx() }
                val state = LibraryBrowserState(folderId = "folder", loading = false)
                Box(Modifier.width(360.dp).height(640.dp).testTag("folder-root")) {
                    LibraryPageLayout(state, true, {}, toolbar = {
                        LibraryToolbar(state, {}, {}, {}, {}, {})
                    }, floatingAction = { source -> LibraryCreateButton(source, true, {}, {}) }) { _, _, _ -> }
                }
            }
        }
        val root = compose.onNodeWithTag("folder-root").fetchSemanticsNode().boundsInRoot
        val create = compose.onNodeWithTag("library-create-action").fetchSemanticsNode().boundsInRoot
        val tools = compose.onNodeWithTag("library-tools").fetchSemanticsNode().boundsInRoot
        assertEquals(root.right - inset, create.right, 1f)
        assertTrue(create.top > root.center.y)
        assertTrue(tools.width <= toolbarLimit)
        compose.onNodeWithContentDescription("搜索整个素材库").assertExists()
        compose.onNodeWithContentDescription("视图与排序").assertExists()
    }
}
