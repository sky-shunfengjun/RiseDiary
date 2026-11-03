package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.*
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryFeedbackRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longFolderTitleKeepsToolbarInHeaderWithoutEllipsis() {
        val name = "一个非常非常长的文件夹名称，用于检查标题不会挤走右侧工具栏"
        compose.setContent {
            RiseDiaryTheme {
                val state = LibraryBrowserState(folderId = "folder", loading = false,
                    snapshot = LibrarySnapshot(folders = listOf(LibraryFolder("folder", null, name, 1))))
                Box(Modifier.width(320.dp).height(600.dp)) {
                    LibraryPageLayout(state, true, {}, toolbar = {
                        LibraryToolbar(state, {}, {}, {}, {}, {})
                    }) { _, _, _ -> }
                }
            }
        }
        val header = compose.onNodeWithTag("rise-page-header").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithContentDescription("搜索整个素材库").fetchSemanticsNode().boundsInRoot
        assertTrue(search.top >= header.top && search.bottom <= header.bottom + 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse(layouts.single().isLineEllipsized(0))
    }

    @Test fun emptyFolderShowsPlaceholderWithoutImportAction() {
        compose.setContent {
            RiseDiaryTheme {
                LibraryBrowserContent(LibraryBrowserState(folderId = "folder", loading = false), LibraryTransferState(),
                    rememberLazyGridState(), 0.dp, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, bottomSpacing = 0.dp)
            }
        }
        compose.onNodeWithText("此文件夹暂无素材").assertIsDisplayed()
        compose.onNodeWithText("导入视频").assertDoesNotExist()
    }

    @Test fun delayedQueryResultsDoNotReplaceTypingOrMoveCursorToStart() {
        var outgoing = ""
        compose.setContent {
            RiseDiaryTheme {
                // The results deliberately keep the previous query while their background work runs.
                LibrarySearchInput("", { outgoing = it }, {})
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("海")
        input.assertTextContains("海")
        input.performTextInput("边")
        input.assertTextContains("海边")
        input.performTextInputSelection(TextRange(1))
        input.performTextInput("中")
        input.assertTextContains("海中边")
        compose.runOnIdle { assertEquals("海中边", outgoing) }
    }
}
