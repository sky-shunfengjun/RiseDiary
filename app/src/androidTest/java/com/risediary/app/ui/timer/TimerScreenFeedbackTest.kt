package com.risediary.app.ui.timer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.UserPreferences
import com.risediary.app.media.VideoPlaybackSnapshot
import com.risediary.app.service.ElapsedRealtimeClock
import com.risediary.app.service.TimerController
import com.risediary.app.service.TimerFinishCandidate
import com.risediary.app.service.TimerSession
import com.risediary.app.service.TimerStartRequest
import com.risediary.app.service.TimerStateHolder
import com.risediary.app.service.TimerStatus
import com.risediary.app.ui.form.RecordFormSessionStore
import com.risediary.app.ui.navigation3.LocalNavigator
import com.risediary.app.ui.navigation3.Navigator
import com.risediary.app.ui.navigation3.Route
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Device UI regression: a failed service write must not draw two messages at the same position. */
class TimerScreenFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()

    @After fun clearModels() {
        compose.runOnIdle { models.clear() }
    }

    @Test fun failedPauseKeepsBothMessagesReadableAndRetryPreservesTheSession() {
        lateinit var fixture: Fixture
        compose.runOnIdle {
            fixture = Fixture()
            models.put("timer_feedback", fixture.model)
        }
        val navigator = Navigator(Route.Main)
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    TimerScreen(fixture.model)
                }
            }
        }
        compose.onNode(hasText("暂停")).performClick()
        compose.waitUntil(5_000) { fixture.model.error != null && !fixture.model.busy }

        val operationMessage = compose.onNode(hasText("未能暂停，请重试"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val persistenceMessage = compose.onNode(hasText("计时状态尚未保存，请重试。"), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("操作失败和未保存说明必须上下排列，不能重叠", operationMessage.bottom <= persistenceMessage.top)

        compose.onNode(hasText("重试")).performClick()
        compose.runOnIdle {
            assertEquals(1, fixture.boundary.retries)
            assertEquals("feedback-session", fixture.holder.state.value.sessionId)
            assertEquals(8_000L, fixture.holder.state.value.elapsedMillis)
            assertEquals(TimerStatus.PAUSED, fixture.holder.state.value.status)
        }
    }

    private class Fixture {
        val holder = TimerStateHolder().apply {
            set(TimerSession(status = TimerStatus.RUNNING, sessionId = "feedback-session",
                startedAtEpochMillis = 100_000L, elapsedMillis = 8_000L))
        }
        val boundary = FailedPauseBoundary(holder)
        private val gate = DataMaintenanceGate()
        private val preferences = UserPreferences(object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }, gate)
        val model = TimerViewModel(boundary, holder, RecordFormSessionStore(gate), preferences,
            Clock.fixed(Instant.ofEpochMilli(120_000L), ZoneOffset.UTC),
            object : ElapsedRealtimeClock { override fun millis() = 30_000L })
    }

    /** Replace only the external Android service acknowledgement; the screen and ViewModel are real. */
    private class FailedPauseBoundary(private val holder: TimerStateHolder) : TimerController {
        override val state = holder.state
        var retries = 0
        override fun pause(sessionId: String) {
            check(sessionId == "feedback-session")
            holder.setPersistenceError(true)
        }
        override fun retryPersistence() {
            retries++
            holder.set(state.value.copy(status = TimerStatus.PAUSED))
            holder.setPersistenceError(false)
        }
        override fun restore() = Unit
        override fun start(request: TimerStartRequest) = error("Unexpected start")
        override fun resume(sessionId: String) = error("Unexpected resume")
        override fun requestFinish(sessionId: String, wallClockNow: Long, elapsedRealtimeNow: Long,
            candidate: TimerFinishCandidate?) = error("Unexpected finish request")
        override fun confirmFinish(sessionId: String) = error("Unexpected finish confirmation")
        override fun cancelFinish(sessionId: String) = error("Unexpected finish cancellation")
        override fun updatePlayback(sessionId: String, snapshot: VideoPlaybackSnapshot, immediate: Boolean) =
            error("Unexpected playback update")
        override fun reset(sessionId: String) = error("Unexpected reset")
        override fun discard(sessionId: String) = error("Unexpected discard")
    }
}
