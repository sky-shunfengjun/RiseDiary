package com.risediary.app.ui.backup

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.risediary.app.data.backup.RestoreCounts
import com.risediary.app.data.backup.RestoreMode
import com.risediary.app.data.backup.RestorePreview
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Requires a physical phone. The sheet follow-up has not been compiled or executed yet. */
class RestorePreviewUiTest {
    @get:Rule val compose = createComposeRule()
    private var preview by mutableStateOf<RestorePreview?>(RestorePreview("test-session", 1, RestoreMode.MERGE,
        RestoreCounts(added = 2, updated = 1, skipped = 1, current = 2, backup = 4, final = 4),
        RestoreCounts(), emptyList()))
    private var busy by mutableStateOf(false)
    private var changes = 0
    private var confirmations = 0
    private var cancellations = 0

    @Test fun selectingNewModeHidesOldCountsAndDisablesConfirmationUntilCalculationFinishes() {
        mount()
        compose.onNodeWithText("覆盖记录").performClick()
        compose.waitForIdle()
        assertEquals(1, changes)
        compose.onNodeWithContentDescription("正在计算恢复概览").assertIsDisplayed()
        compose.onNodeWithText("恢复后 4 条").assertDoesNotExist()
        compose.onNodeWithText("合并记录").assertDoesNotExist()
        compose.onNodeWithText("确认恢复").assertIsNotEnabled()
        assertEquals(0, confirmations)
        compose.runOnIdle {
            preview = checkNotNull(preview).copy(revision = 2, mode = RestoreMode.REPLACE,
                flights = RestoreCounts(current = 2, backup = 5, final = 5))
            busy = false
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("正在计算恢复概览").assertDoesNotExist()
        compose.onNodeWithText("恢复备份 5 条").assertIsDisplayed()
        compose.onNodeWithText("确认恢复").assertIsEnabled().performClick()
        assertEquals(1, confirmations)
    }

    @Test fun tappingSelectedModeDoesNotRecalculateOrStartLoading() {
        mount()
        compose.onNodeWithText("合并记录").performClick()
        compose.waitForIdle()
        assertEquals(0, changes)
        compose.onNodeWithText("恢复后 4 条").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在计算恢复概览").assertDoesNotExist()
    }

    @Test fun largeTextKeepsBothModeCardsOnTheSameRow() {
        mount(fontScale = 1.6f)
        val merge = compose.onNodeWithText("合并记录").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val replace = compose.onNodeWithText("覆盖记录").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Cards must stay horizontally separated", merge.right <= replace.left)
        assertEquals(merge.top, replace.top, 2f)
    }

    @Test fun closeButtonDismissesSheetWithoutConfirmingRestore() {
        mount()
        compose.onNodeWithTag("restore_preview_sheet").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭恢复概览").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("restore_preview_sheet").assertDoesNotExist()
        assertEquals(1, cancellations)
        assertEquals(0, confirmations)
    }

    @Test fun calculationDisablesCloseAndOutsideTapDoesNotDiscardPreview() {
        mount()
        compose.onNodeWithText("覆盖记录").performClick()
        compose.onNodeWithContentDescription("关闭恢复概览").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onRoot().performTouchInput { click(Offset(center.x, 4f)) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("正在计算恢复概览").assertIsDisplayed()
        assertEquals(0, cancellations)
        assertEquals(0, confirmations)
    }

    private fun mount(fontScale: Float = 1f) {
        compose.setContent {
            MiuixTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    RestorePreviewSheet(preview, busy, "", changeMode = {
                        changes++
                        busy = true
                    }, confirm = { confirmations++ }, cancel = {
                        cancellations++
                        preview = null
                    })
                }
            }
        }
        compose.waitForIdle()
    }
}
