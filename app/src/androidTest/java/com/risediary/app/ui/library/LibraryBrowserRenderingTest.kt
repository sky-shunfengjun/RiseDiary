package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.risediary.app.data.library.LibraryEntry
import com.risediary.app.data.library.LibraryFolder
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryBrowserRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun narrowLargeTextItemKeepsFullNameSemanticsPathAndTapBehavior() {
        var clicks = 0
        val name = "很长很长的文件夹名称，需要在列表中省略但保留完整阅读"
        compose.setContent {
            RiseDiaryTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.width(280.dp)) {
                        LibraryBrowserItem(LibraryEntry("folder:a", name, null, "素材库 / 旅行", folder = LibraryFolder("a", null, name, 1)),
                            list = true, showPath = true, selected = true, enabled = true,
                            onClick = { clicks++ }, onSelect = {}, onRename = {}, onMove = {}, onDelete = {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(name).assertIsSelected().performClick()
        compose.onNodeWithText("素材库 / 旅行", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
