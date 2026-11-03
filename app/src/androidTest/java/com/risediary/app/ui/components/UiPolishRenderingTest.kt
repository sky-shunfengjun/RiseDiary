package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real phone checks; compilation alone does not establish rendering correctness. */
class UiPolishRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bodyFollowsMeasuredHeaderWithOneExplicitInsetAtLargeFont() {
        var inset by mutableIntStateOf(0)
        var dp = 1f
        compose.setContent {
            val density = LocalDensity.current
            dp = density.density
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                RiseDiaryTheme {
                    SecondaryPageScaffold("较长的页面标题", {}, Modifier.width(320.dp).height(500.dp),
                        windowInsets = WindowInsets(0, inset, 0, 0)) { padding ->
                        Box(Modifier.padding(padding).size(40.dp).testTag("body"))
                    }
                }
            }
        }
        fun gap(): Float {
            val header = compose.onNodeWithTag("rise-page-header").fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
            return body.top - header.bottom
        }
        compose.waitForIdle()
        assertEquals(16f * dp, gap(), 2f)
        compose.runOnIdle { inset = (60 * dp).toInt() }
        compose.waitForIdle()
        assertEquals("Host inset must not create a second blank band", 16f * dp, gap(), 2f)
    }

    @Test fun dialogWindowReceivesItsOwnLightThemeSystemBarAppearance() {
        var window: android.view.Window? = null
        compose.setContent {
            RiseDiaryTheme("dark") {
                Dialog({}) {
                    RiseDiaryTheme("light") {
                        val view = LocalView.current
                        SideEffect { window = (view.parent as? DialogWindowProvider)?.window }
                        Box(Modifier.size(120.dp))
                    }
                }
            }
        }
        compose.runOnIdle {
            val current = requireNotNull(window)
            assertTrue(WindowCompat.getInsetsController(current, current.decorView).isAppearanceLightStatusBars)
        }
    }

    @Test fun emphasizedActionHasCapsuleEndsInsteadOfSmallRoundedCorners() {
        val page = Color(0xFF00AA22)
        compose.setContent {
            RiseDiaryTheme {
                Box(Modifier.width(220.dp).background(page).testTag("capsule-page")) {
                    RiseDialogActionButton(RiseDialogAction("确认", {}, DialogActionTone.Normal),
                        Modifier.fillMaxWidth().testTag("capsule"), emphasized = true)
                }
            }
        }
        val pixels = compose.onNodeWithTag("capsule-page").captureToImage().toPixelMap()
        val corner = pixels[pixels.height / 8, pixels.height / 8]
        assertEquals(page.green, corner.green, .05f)
        val endCenter = pixels[pixels.height / 8, pixels.height / 2]
        assertTrue("Capsule must still paint its rounded end", endCenter.blue > endCenter.green)
    }
}
