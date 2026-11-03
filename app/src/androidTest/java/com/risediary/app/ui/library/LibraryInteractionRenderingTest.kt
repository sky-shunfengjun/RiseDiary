package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.*
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryInteractionRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun closingSearchDismissesTheNativeBottomSheet() {
        compose.setContent {
            RiseDiaryTheme {
                var visible by remember { mutableStateOf(true) }
                LibrarySearchSheet(LibraryBrowserState(searching = visible, loading = false), {}, { visible = false }, {}, {},
                    {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("关闭搜索").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("library-search-sheet").assertDoesNotExist()
    }
    @Test fun miuixSearchFieldAcceptsQueryAndItsSearchAction() {
        var submitted = false
        compose.setContent {
            RiseDiaryTheme {
                var query by remember { mutableStateOf("") }
                LibrarySearchInput(query, { query = it }, { submitted = true })
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("海边")
        compose.onNode(hasSetTextAction()).assertTextContains("海边").performImeAction()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test fun menuTextIsInsetAndAlignedAcrossViewAndSortGroups() {
        var picked: LibraryViewMode? = null
        var minimumInset = 0f
        compose.setContent {
            RiseDiaryTheme {
                minimumInset = with(LocalDensity.current) { 48.dp.toPx() }
                Column(Modifier.width(280.dp).padding(8.dp)) {
                    LibraryViewMenu(LibraryViewMode.LIST, LibrarySort.NAME, { picked = it }, {})
                }
            }
        }
        val view = compose.onNodeWithText("列表", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val sort = compose.onNodeWithText("按名称", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(view.left, sort.left, 1f)
        assertTrue(view.left >= minimumInset)
        compose.onNodeWithText("列表").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(LibraryViewMode.LIST, picked) }
    }
}
