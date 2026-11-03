package com.risediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Rule
import org.junit.Test

/** Actual rendered contrast; these checks require a connected physical phone. */
class BottomBarContrastRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightThemeUnselectedLabelAdaptsWhenCoverChangesFromBlackToWhite() {
        var cover by mutableStateOf(Color.Black)
        compose.setContent { RiseDiaryTheme("light") {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.width(360.dp).height(180.dp)) {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(cover))
                LiquidGlassBottomBar(1, {}, {}, backdrop, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        } }
        awaitInk("首页", true)
        compose.runOnIdle { cover = Color.White }
        awaitInk("首页", false)
    }

    @Test fun eachTabUsesItsOwnBackgroundInsteadOfAnAverageOfTheWholeBar() {
        compose.setContent { RiseDiaryTheme("light") {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.width(360.dp).height(180.dp)) {
                Row(Modifier.matchParentSize().layerBackdrop(backdrop)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
                }
                LiquidGlassBottomBar(1, {}, {}, backdrop, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        } }
        awaitInk("首页", true)
        awaitInk("设置", false)
    }

    @Test fun darkThemeStillUsesBlackLabelsOverAWhiteCoverAndFlightRemainsClickable() {
        var flights = 0
        compose.setContent { RiseDiaryTheme("dark") {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.width(360.dp).height(180.dp)) {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(Color.White))
                LiquidGlassBottomBar(1, {}, { flights++ }, backdrop, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
        } }
        awaitInk("首页", false)
        compose.onNodeWithContentDescription("开始起飞").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, flights) }
    }

    private fun awaitInk(label: String, light: Boolean) {
        compose.waitUntil(timeoutMillis = 6000) {
            val pixels = compose.onNodeWithText(label).captureToImage().toPixelMap()
            var ink = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (if (light) color.red > .9f && color.green > .9f && color.blue > .9f
                    else color.red < .1f && color.green < .1f && color.blue < .1f) ink++
            }
            ink > 8
        }
    }
}
