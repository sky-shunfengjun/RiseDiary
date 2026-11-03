package com.risediary.app.ui.library

import androidx.media3.common.Player
import com.risediary.app.media.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class LibraryPlaybackSessionTest {
    @Test fun newLibraryVideoLoopsButRestoringAnExplicitNonLoopingChoiceKeepsItOff() {
        val player = RecordingPlayer()
        val session = LibraryPlaybackSession(player)
        session.select(video("new"), allowAutoplay = false)
        assertTrue("A newly opened library video must repeat by default", player.loaded.single().loop)
        session.select(video("saved"), false, VideoPlaybackSnapshot(video("saved"), loop = false))
        assertFalse("Restoring a timer must retain the user's loop choice", player.loaded.last().loop)
    }
    @Test fun aLateSelectionFromBeforeBackgroundDoesNotAutoplayEvenAfterReturning() {
        val player = RecordingPlayer()
        val session = LibraryPlaybackSession(player)
        val requested = session.autoplayGeneration
        session.suspendAutoplay()
        session.setActive(true)
        session.select(video("late"), allowAutoplay = true, autoplayGeneration = requested)
        assertEquals(0, player.plays)
        session.select(video("next"), allowAutoplay = true, autoplayGeneration = session.autoplayGeneration)
        assertEquals(1, player.plays)
    }
    @Test fun restoringATimedMaterialKeepsProgressSpeedAndLoopWithoutAutoplayOrReloadOnStart() {
        val player = RecordingPlayer()
        val session = LibraryPlaybackSession(player)
        val saved = VideoPlaybackSnapshot(video("one"), 987L, 1.5f, true)
        session.select(saved.video, allowAutoplay = false, restored = saved)
        session.setActive(true)
        assertEquals(listOf(saved), player.loaded)
        assertEquals(0, player.plays)
        session.select(saved.video, allowAutoplay = false, restored = saved)
        assertEquals(1, player.loaded.size)
    }
    @Test fun firstVideoAutoplaysWhenEntryTransitionBecomesVisibleButReturningDoesNot() {
        val player = RecordingPlayer()
        val session = LibraryPlaybackSession(player)
        session.select(video("one"), allowAutoplay = true)
        assertEquals(0, player.plays)
        session.setActive(true)
        assertEquals(1, player.plays)
        session.setActive(false)
        session.setActive(true)
        assertEquals(1, player.plays)
        session.select(video("two"), allowAutoplay = true)
        assertEquals(2, player.plays)
        assertEquals(listOf("one", "two"), player.loaded.map { it.video.displayName })
    }
    @Test fun backgroundDiscardsPendingAutoplayAndRepeatedSelectionDoesNotReload() {
        val player = RecordingPlayer()
        val session = LibraryPlaybackSession(player)
        session.select(video("one"), allowAutoplay = true)
        session.suspendAutoplay()
        session.setActive(true)
        assertEquals(0, player.plays)
        session.setActive(false)
        session.select(video("two"), allowAutoplay = false)
        session.setActive(true)
        session.select(video("two"), allowAutoplay = true)
        assertEquals(0, player.plays)
        assertEquals(2, player.loaded.size)
    }
    private fun video(id: String) = LocalVideoRef("content://library/video/$id", id, "video/mp4")
    private class RecordingPlayer : VideoPlayerController {
        val loaded = mutableListOf<VideoPlaybackSnapshot>()
        var plays = 0
        override val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ -> null } as Player
        override val playback = MutableStateFlow<VideoPlaybackSnapshot?>(null)
        override val interactions = MutableSharedFlow<VideoPlaybackSnapshot>()
        override val isPlaying = MutableStateFlow(false)
        override val error = MutableStateFlow<String?>(null)
        override fun load(snapshot: VideoPlaybackSnapshot) { loaded.add(snapshot); playback.value = snapshot }
        override fun play() { plays++; isPlaying.value = true }
        override fun pause() { isPlaying.value = false }
        override fun seekTo(positionMillis: Long) {}
        override fun setSpeed(speed: Float) {}
        override fun setLoop(loop: Boolean) {}
        override fun release() {}
    }
}
