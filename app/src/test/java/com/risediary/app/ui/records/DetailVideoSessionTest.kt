package com.risediary.app.ui.records

import androidx.media3.common.Player
import com.risediary.app.media.*
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailVideoSessionTest {
    private val video = LocalVideoRef("content://videos/old", "old.mp4", "video/mp4")

    @Test fun settingsMustBeKnownBeforePreparingOrShowingVideo() = runTest {
        val settings = MutableSharedFlow<Boolean>()
        val player = FakeController()
        val session = DetailVideoSession(backgroundScope, settings, { VideoAccessState.READABLE }, player)
        session.bind(1, video)
        runCurrent()
        assertTrue(session.state.value.hidden)
        assertFalse(session.state.value.settingsReady)
        assertTrue(player.loads.isEmpty())
        settings.emit(false)
        runCurrent()
        assertFalse(session.state.value.hidden)
        assertEquals(listOf(VideoPlaybackSnapshot(video)), player.loads)
        assertFalse(player.isPlaying.value)
    }

    @Test fun defaultHiddenDoesNotPrepareUntilManualReveal() = runTest {
        val player = FakeController()
        val session = session(player, hidden = true)
        runCurrent()
        assertTrue(player.loads.isEmpty())
        session.show()
        runCurrent()
        assertEquals(listOf(VideoPlaybackSnapshot(video)), player.loads)
        assertFalse(player.isPlaying.value)
        assertTrue(session.state.value.prepared)
    }

    @Test fun hidePausesAndRevealRetainsProgressSpeedAndLoop() = runTest {
        val player = FakeController()
        val session = session(player)
        runCurrent()
        player.seekTo(530)
        player.setSpeed(1.5f)
        player.setLoop(true)
        player.play()
        session.hide()
        assertFalse(player.isPlaying.value)
        assertTrue(session.state.value.hidden)
        session.show()
        runCurrent()
        assertEquals(1, player.loads.size)
        assertEquals(VideoPlaybackSnapshot(video, 530, 1.5f, true), player.playback.value)
        assertFalse(player.isPlaying.value)
    }

    @Test fun backgroundRehidesWhenPreferenceIsEnabled() = runTest {
        val player = FakeController()
        val session = session(player, hidden = true)
        runCurrent()
        session.show()
        runCurrent()
        player.play()
        session.onBackground()
        assertTrue(session.state.value.hidden)
        assertFalse(player.isPlaying.value)
        session.show()
        runCurrent()
        assertEquals(1, player.loads.size)
        assertFalse(player.isPlaying.value)
    }

    @Test fun backgroundOnlyPausesWhenPreferenceIsDisabled() = runTest {
        val player = FakeController()
        val session = session(player)
        runCurrent()
        player.play()
        session.onBackground()
        assertFalse(session.state.value.hidden)
        assertFalse(player.isPlaying.value)
    }

    @Test fun settingsFailureStaysCoveredAndRetryUsesActualSetting() = runTest {
        var fail = true
        val player = FakeController()
        val session = DetailVideoSession(backgroundScope, flow {
            if (fail) throw IOException("unreadable")
            emit(true)
        }, { VideoAccessState.READABLE }, player)
        session.bind(1, video)
        runCurrent()
        assertTrue(session.state.value.settingsFailed)
        assertTrue(session.state.value.hidden)
        session.show()
        assertTrue(player.loads.isEmpty())
        fail = false
        session.retry()
        runCurrent()
        assertTrue(session.state.value.settingsReady)
        assertTrue(session.state.value.hidden)
        assertTrue(player.loads.isEmpty())
    }

    @Test fun lateOldAccessResultCannotReplaceRelinkedVideo() = runTest {
        val player = FakeController()
        val next = video.copy(uriString = "content://videos/new", displayName = "new.mp4")
        val session = DetailVideoSession(backgroundScope, flowOf(false), { ref ->
            if (ref == video) withContext(NonCancellable) { delay(1_000) }
            VideoAccessState.READABLE
        }, player)
        session.bind(1, video)
        runCurrent()
        session.bind(1, next)
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(VideoPlaybackSnapshot(next)), player.loads)
        assertEquals(next, session.state.value.video)
        assertTrue(session.state.value.prepared)
    }

    @Test fun hidingWhileAccessIsPendingPreventsPreparation() = runTest {
        val player = FakeController()
        val session = DetailVideoSession(backgroundScope, flowOf(false), {
            withContext(NonCancellable) { delay(500) }
            VideoAccessState.READABLE
        }, player)
        session.bind(1, video)
        runCurrent()
        session.hide()
        advanceTimeBy(501)
        runCurrent()
        assertTrue(player.loads.isEmpty())
        assertTrue(session.state.value.hidden)
        assertFalse(session.state.value.loading)
    }

    @Test fun unreadableVideoCanRetryWithoutLosingRecordAssociation() = runTest {
        val player = FakeController()
        var readable = false
        val session = DetailVideoSession(backgroundScope, flowOf(false), {
            if (readable) VideoAccessState.READABLE else VideoAccessState.MISSING
        }, player)
        session.bind(1, video)
        runCurrent()
        assertNotNull(session.state.value.problem)
        assertTrue(player.loads.isEmpty())
        readable = true
        session.retry()
        runCurrent()
        assertNull(session.state.value.problem)
        assertEquals(listOf(VideoPlaybackSnapshot(video)), player.loads)
    }

    @Test fun aDifferentRecordUsingSameVideoStartsFresh() = runTest {
        val player = FakeController()
        val session = session(player)
        runCurrent()
        player.seekTo(600)
        player.setSpeed(2f)
        player.setLoop(true)
        session.bind(2, video)
        runCurrent()
        assertEquals(VideoPlaybackSnapshot(video), player.playback.value)
        assertEquals(2, player.loads.size)
    }

    @Test fun playbackRetryRetainsThisSessionsPositionAndPreferences() = runTest {
        val player = FakeController()
        val session = session(player)
        runCurrent()
        player.seekTo(321)
        player.setSpeed(2f)
        player.setLoop(true)
        player.error.value = "cannot decode"
        runCurrent()
        assertNotNull(session.state.value.problem)
        session.retry()
        runCurrent()
        assertEquals(VideoPlaybackSnapshot(video, 321, 2f, true), player.playback.value)
        assertFalse(player.isPlaying.value)
    }

    @Test fun revealingAfterAnErrorWhileHiddenRepreparesInsteadOfOfferingAnUnusablePlayButton() = runTest {
        val settings = MutableStateFlow(true)
        val player = FakeController()
        val session = DetailVideoSession(backgroundScope, settings, { VideoAccessState.READABLE }, player)
        session.bind(1, video)
        runCurrent()
        session.show()
        runCurrent()
        session.hide()
        player.error.value = "decoder stopped"
        runCurrent()
        settings.value = false
        runCurrent()
        session.show()
        runCurrent()
        assertEquals(2, player.loads.size)
        assertNull(session.state.value.problem)
        assertNull(player.error.value)
        assertFalse(player.isPlaying.value)
    }

    @Test fun relinkingBackToAnEarlierVideoStartsAtZeroEvenIfIntermediateLoadWasCancelled() = runTest {
        val player = FakeController()
        val another = video.copy(uriString = "content://videos/new", displayName = "new.mp4")
        val session = DetailVideoSession(backgroundScope, flowOf(false), { ref ->
            if (ref == another) delay(1_000)
            VideoAccessState.READABLE
        }, player)
        session.bind(1, video)
        runCurrent()
        player.seekTo(650)
        session.bind(1, another)
        runCurrent()
        session.bind(1, video)
        runCurrent()
        assertEquals(VideoPlaybackSnapshot(video), player.playback.value)
    }

    @Test fun hiddenAndBackgroundPagesRelinquishPresentationThenReturnWithoutPlaying() = runTest {
        val player = FakeController()
        val session = session(player)
        runCurrent()
        player.seekTo(900); player.setSpeed(1.5f); player.setLoop(true)
        session.hide()
        assertFalse(player.isPresented)
        session.show(); runCurrent()
        assertTrue(player.isPresented)
        session.onBackground()
        assertFalse(player.isPresented)
        session.setPresentationActive(true)
        assertTrue(player.isPresented)
        assertEquals(VideoPlaybackSnapshot(video, 900, 1.5f, true), player.playback.value)
        assertFalse(player.isPlaying.value)
    }

    @Test fun lateAccessCheckCannotReprepareThePreviousRecord() = runTest {
        val player = FakeController()
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val another = video.copy(uriString = "content://video/new", displayName = "new")
        val session = DetailVideoSession(backgroundScope, flowOf(false), { ref ->
            if (ref == another) ready.await()
            VideoAccessState.READABLE
        }, player)
        session.bind(1, video); runCurrent()
        session.bind(2, another); runCurrent()
        session.setPresentationActive(true)
        assertFalse(player.isPresented)
        ready.complete(Unit); runCurrent()
        assertTrue(player.isPresented)
        assertEquals(another, player.playback.value!!.video)
    }

    private fun TestScope.session(player: FakeController, hidden: Boolean = false) =
        DetailVideoSession(backgroundScope, flowOf(hidden), { VideoAccessState.READABLE }, player)
            .also { it.bind(1, video) }

    /** Media boundary only; session, cancellation and visibility rules are real. */
    private class FakeController : VideoPlayerController {
        val loads = mutableListOf<VideoPlaybackSnapshot>()
        var isPresented = false
        override fun setPresentationActive(active: Boolean) { isPresented = active; if (!active) pause() }
        override val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader,
            arrayOf(Player::class.java)) { _, _, _ -> error("Native player is not used by session logic") } as Player
        override val playback = MutableStateFlow<VideoPlaybackSnapshot?>(null)
        override val interactions = MutableSharedFlow<VideoPlaybackSnapshot>()
        override val isPlaying = MutableStateFlow(false)
        override val error = MutableStateFlow<String?>(null)
        override fun load(snapshot: VideoPlaybackSnapshot) { pause(); error.value = null; loads += snapshot; playback.value = snapshot }
        override fun play() { isPlaying.value = true }
        override fun pause() { isPlaying.value = false }
        override fun seekTo(positionMillis: Long) { playback.value = playback.value?.copy(positionMillis = positionMillis) }
        override fun setSpeed(speed: Float) { playback.value = playback.value?.copy(speed = speed) }
        override fun setLoop(loop: Boolean) { playback.value = playback.value?.copy(loop = loop) }
        override fun release() { pause() }
    }
}
