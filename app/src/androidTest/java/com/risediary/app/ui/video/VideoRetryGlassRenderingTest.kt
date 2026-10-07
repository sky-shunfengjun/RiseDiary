package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises a real enclosing capture, the same ownership as SecondaryPageScaffold.
 * Drawing and pressing retries must not feed their glass back into its own source layer.
 * Device execution is required; compiling this test does not verify RenderThread safety.
 */
class VideoRetryGlassRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val retryText get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_retry)

    @Test fun nonFullscreenVideoErrorCanRenderAndRetryInsidePageCapture() {
        var retries = 0
        compose.setContent {
            RiseDiaryTheme(themeMode = "dark") {
                val pageBackdrop = rememberLayerBackdrop()
                Box(Modifier.width(320.dp).layerBackdrop(pageBackdrop).testTag("captured")) {
                    VideoPlaceholderCard(loading = false, loadingIndicator = false,
                        problem = "读取失败", timerMode = true, backdrop = pageBackdrop, onRetry = { retries++ })
                }
            }
        }
        assertTrue(compose.onNodeWithTag("captured").captureToImage().width > 0)
        compose.onNodeWithText(retryText).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        assertTrue(compose.onNodeWithTag("captured").captureToImage().height > 0)
    }

    @Test fun timerPersistenceErrorCanRenderAndRetryInsidePageCapture() {
        var retries = 0
        compose.setContent {
            RiseDiaryTheme(themeMode = "light") {
                val pageBackdrop = rememberLayerBackdrop()
                Box(Modifier.width(320.dp).layerBackdrop(pageBackdrop).testTag("captured")) {
                    VideoTimerSummary(session = TimerSession(status = TimerStatus.PAUSED,
                        startedAtEpochMillis = 10_000, elapsedMillis = 2_000, sessionId = "session"),
                        fullScreen = false, notice = null, problem = null, persistenceError = true,
                        backdrop = pageBackdrop, onRetry = { retries++ })
                }
            }
        }
        assertTrue(compose.onNodeWithTag("captured").captureToImage().width > 0)
        compose.onNodeWithText(retryText).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        assertTrue(compose.onNodeWithTag("captured").captureToImage().height > 0)
    }
}
