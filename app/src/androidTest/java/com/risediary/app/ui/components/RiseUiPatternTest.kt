package com.risediary.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.catalog.RiseUiPattern
import com.risediary.app.ui.catalog.UiPatternKind
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** These checks must actually run on a connected phone; compilation is not visual acceptance. */
class RiseUiPatternTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingAPatternActivatesItsExistingProgressiveBlur() {
        compose.setContent {
            RiseDiaryTheme {
                RisePatternPage("滚动样板", {}, Modifier.width(320.dp).height(360.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)) {
                    repeat(40) { top.yukonga.miuix.kmp.basic.Text("测试条目 $it") }
                }
            }
        }
        compose.onNodeWithTag("rise-page-top-blur").assertDoesNotExist()
        compose.onNodeWithText("测试条目 35").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("rise-page-top-blur").assertExists()
        compose.onNode(hasScrollAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
            it(0f, -100000f)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("rise-page-top-blur").assertDoesNotExist()
    }

    @Test fun mixedIconRowsShareTheSameNameAndTrailingEdges() {
        compose.setContent {
            RiseDiaryTheme {
                Column(Modifier.width(300.dp)) {
                    RiseListItem("有图标", {}, icon = AppIcons.Notes, reserveIconSpace = true)
                    RiseListItem("无图标", {}, reserveIconSpace = true)
                }
            }
        }
        val first = compose.onNodeWithText("有图标", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("无图标", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 1f)
    }

    @Test fun overflowKeepsActionsReachableAndDisabledActionsDisabled() {
        var adds = 0
        var searches = 0
        compose.setContent {
            RiseDiaryTheme {
                RiseTopActions(listOf(
                    RiseTopAction("新增", AppIcons.Add, { adds++ }),
                    RiseTopAction("搜索", AppIcons.Filter, { searches++ }),
                    RiseTopAction("停用项", AppIcons.Delete, {}, enabled = false)
                ), availableSlots = 2)
            }
        }
        compose.onNodeWithContentDescription("新增").performClick()
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("停用项").assertIsNotEnabled()
        compose.onNodeWithText("搜索").performClick()
        compose.runOnIdle { assertEquals(1, adds); assertEquals(1, searches) }
        compose.onNodeWithText("搜索").assertDoesNotExist()
    }

    @Test fun formShowsItsLocalErrorAndCanScrollLastFieldAboveSaveAction() {
        compose.setContent {
            RiseDiaryTheme { RiseUiPattern(UiPatternKind.Form, Modifier.width(320.dp).height(560.dp)) }
        }
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("请填写名称").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("示例")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithTag("pattern-saved").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pattern-last-field").performScrollTo().assertIsDisplayed()
        val last = compose.onNodeWithTag("pattern-last-field").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithTag("pattern-save").fetchSemanticsNode().boundsInRoot
        assertTrue("last field must be reachable above floating save", last.bottom <= save.top + 1f)
    }

    @Test fun allPatternsMountInBothThemesAndLargeTextWithoutMissingHosts() {
        var kind by mutableStateOf(UiPatternKind.Settings)
        var mode by mutableStateOf("light")
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                RiseDiaryTheme(mode) { key(kind) { RiseUiPattern(kind, Modifier.width(320.dp).height(560.dp)) } }
            }
        }
        val titles = listOf("设置样板", "填写样板", "内容样板", "统计样板")
        for (theme in listOf("light", "dark")) for (font in listOf(1f, 1.6f, 2f))
            for ((index, next) in UiPatternKind.entries.withIndex()) {
                compose.runOnIdle { mode = theme; scale = font; kind = next }
                compose.waitForIdle()
                compose.onNodeWithText(titles[index]).assertIsDisplayed()
            }
    }
}
