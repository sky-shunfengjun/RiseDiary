package com.risediary.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Tests complete text and real clipboard; only compilation is possible without a phone. */
class ErrorMessageCopyTest {
    @get:Rule val compose = createComposeRule()
    private val message = "备份未通过校验：" + "完整错误内容\n".repeat(40)
    private val host = SnackbarHostState()
    private var result: SnackbarResult? = null

    @Test fun longPressCopiesEntireErrorIncludingEllipsizedLinesAndDoesNotDismissIt() {
        mount()
        compose.onNodeWithText(message).performTouchInput { longClick() }
        compose.runOnIdle {
            val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            assertEquals(message, clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertNotNull(host.currentSnackbarData)
            assertNull(result)
        }
    }

    @Test fun normalRetryActionStillWorksWithoutLongPress() {
        mount()
        compose.onNodeWithText("重试").performTouchInput { click() }
        compose.waitUntil { result != null }
        assertEquals(SnackbarResult.ActionPerformed, result)
    }

    private fun mount() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MiuixTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                    LiquidSnackbarHost(host, backdrop)
                }
                LaunchedEffect(Unit) {
                    result = host.showLiquidSnackbar(message, "重试", LiquidSnackbarTone.ERROR)
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }
}
