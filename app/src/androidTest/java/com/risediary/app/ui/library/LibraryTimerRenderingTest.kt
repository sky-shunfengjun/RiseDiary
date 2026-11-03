package com.risediary.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.service.*
import com.risediary.app.ui.theme.RiseDiaryTheme
import com.risediary.app.ui.timer.TimerActionDock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryTimerRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun startButtonIsExplicitAndCannotBeClickedAgainWhileStarting() {
        var starts = 0
        var starting by mutableStateOf(false)
        compose.setContent { RiseDiaryTheme {
            LibraryTimerControls(null, starting, rememberLayerBackdrop(), { starts++; starting = true }, {}, {}, true,
                modifier = Modifier)
        } }
        compose.onNodeWithContentDescription("开始计时").performClick()
        compose.onNodeWithContentDescription("正在开始…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun bottomShortcutFinishesRunningOrPausedTimerWithoutAPauseButton() {
        var status by mutableStateOf(TimerStatus.RUNNING)
        var finished = false
        compose.setContent { RiseDiaryTheme {
            LibraryTimerControls(TimerSession(status = status, sessionId = "s", elapsedMillis = 5000L), false,
                rememberLayerBackdrop(), {}, { finished = true }, {}, true)
        } }
        compose.onNodeWithText("开始计时").assertDoesNotExist()
        compose.onNodeWithContentDescription("暂停").assertDoesNotExist()
        compose.onNodeWithContentDescription("继续").assertDoesNotExist()
        compose.onNodeWithContentDescription("我已起飞").performClick()
        compose.runOnIdle { assertTrue(finished); finished = false; status = TimerStatus.PAUSED }
        compose.onNodeWithContentDescription("我已起飞").performClick()
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test fun expandedTimerOffersPauseAndFinishTogetherAndResumeWhenPaused() {
        var status by mutableStateOf(TimerStatus.RUNNING)
        var finished = false
        compose.setContent { RiseDiaryTheme {
            TimerActionDock(TimerSession(status = status, sessionId = "s"), rememberLayerBackdrop(), {},
                { status = TimerStatus.PAUSED }, { status = TimerStatus.RUNNING }, { finished = true }, {},
                fullScreen = true, iconOnly = true, finishWhileRunning = true)
        } }
        compose.onNodeWithContentDescription("我已起飞").assertIsDisplayed()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithContentDescription("继续").performClick()
        compose.onNodeWithContentDescription("我已起飞").performClick()
        compose.runOnIdle { assertEquals(TimerStatus.RUNNING, status); assertTrue(finished) }
    }
}
