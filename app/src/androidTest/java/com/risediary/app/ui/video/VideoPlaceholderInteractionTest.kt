package com.risediary.app.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.ui.theme.RiseDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Empty-region taps reuse selection; busy, error and detail states must not open a picker. */
class VideoPlaceholderInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyCardCornerOpensSelectionAndStopsAcceptingTapsWhileBusy() {
        val busy = mutableStateOf(false)
        var selections = 0
        compose.setContent {
            RiseDiaryTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.width(300.dp)) {
                    Box(Modifier.matchParentSize().layerBackdrop(backdrop))
                    VideoPlaceholderCard(loading = busy.value, loadingIndicator = false,
                        problem = null, timerMode = true, backdrop = backdrop, onRetry = {},
                        onChooseVideo = { selections++; busy.value = true },
                        modifier = Modifier.testTag("placeholder"))
                }
            }
        }
        // Hit blank space near the rounded top corner, away from the existing icon and title.
        tapCorner()
        compose.runOnIdle { assertEquals(1, selections) }
        tapCorner()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun loadingWithoutSpinnerDoesNotOpenSelection() = checkUnavailable(loading = true)

    @Test fun errorCardDoesNotOpenSelection() = checkUnavailable(problem = "读取失败")

    @Test fun readOnlyPreviewDoesNotAcquireASelectionAction() = checkUnavailable(readOnly = true)

    private fun checkUnavailable(loading: Boolean = false, problem: String? = null, readOnly: Boolean = false) {
        var selections = 0
        compose.setContent {
            RiseDiaryTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.width(300.dp)) {
                    Box(Modifier.matchParentSize().layerBackdrop(backdrop))
                    VideoPlaceholderCard(loading, false, problem, !readOnly, backdrop, {},
                        onChooseVideo = if (readOnly) null else ({ selections++ }),
                        modifier = Modifier.testTag("placeholder"))
                }
            }
        }
        tapCorner()
        compose.runOnIdle { assertEquals(0, selections) }
    }

    private fun tapCorner() {
        compose.onNodeWithTag("placeholder").performTouchInput { click(Offset(35f, 35f)) }
    }
}
