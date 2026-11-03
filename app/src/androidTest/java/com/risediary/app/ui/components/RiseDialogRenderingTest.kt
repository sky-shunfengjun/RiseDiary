package com.risediary.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Device rendering is required; compiling this class does not verify its pixels. */
class RiseDialogRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmationKeepsItsColoredSurfaceAndWhiteLabelInBothThemes() {
        var dark by mutableStateOf(false)
        var dangerous by mutableStateOf(false)
        compose.setContent {
            RiseDiaryTheme(if (dark) "dark" else "light") {
                Box(Modifier.width(300.dp)) {
                    RiseDialogActions(
                        RiseDialogAction("确认", {}, if (dangerous) DialogActionTone.Destructive else DialogActionTone.Normal),
                        dismissAction = RiseDialogAction("取消", {}, DialogActionTone.Normal)
                    )
                }
            }
        }
        for (nextDark in listOf(false, true)) for (nextDanger in listOf(false, true)) {
            compose.runOnIdle { dark = nextDark; dangerous = nextDanger }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("rise-dialog-confirm").captureToImage().toPixelMap()
            val surface = pixels[pixels.width / 2, pixels.height / 5]
            if (nextDanger) assertTrue("danger surface must be red", surface.red > surface.blue)
            else assertTrue("normal surface must be blue, not a white recolored button", surface.blue > surface.red + .2f)
            var white = 0
            for (y in pixels.height / 4 until pixels.height * 3 / 4)
                for (x in pixels.width / 10 until pixels.width * 9 / 10) {
                    val p = pixels[x, y]
                    if (p.red > .92f && p.green > .92f && p.blue > .92f) white++
                }
            assertTrue("white label must survive surface painting", white > 3)
            val secondary = compose.onNodeWithTag("rise-dialog-dismiss").captureToImage().toPixelMap()
            val secondarySurface = secondary[secondary.width / 2, secondary.height / 5]
            if (nextDark) assertTrue(secondarySurface.red < .4f)
            else assertTrue("secondary surface is opaque white", secondarySurface.red > .95f)
        }
    }

    @Test fun disabledActionsCannotDispatchAndThirdActionKeepsItsOwnIntent() {
        var enabled by mutableStateOf(false)
        var confirms = 0
        var discards = 0
        compose.setContent {
            RiseDiaryTheme {
                Box(Modifier.width(280.dp)) {
                    RiseDialogActions(
                        RiseDialogAction("保存", { confirms++ }, DialogActionTone.Normal, enabled),
                        dismissAction = RiseDialogAction("取消", {}, DialogActionTone.Normal),
                        neutralAction = RiseDialogAction("放弃", { discards++ }, DialogActionTone.Destructive)
                    )
                }
            }
        }
        compose.onNodeWithText("保存").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, confirms); enabled = true }
        compose.onNodeWithText("保存").assertIsEnabled().performClick()
        compose.onNodeWithText("放弃").performClick()
        compose.runOnIdle { assertEquals(1, confirms); assertEquals(1, discards) }
    }
}
