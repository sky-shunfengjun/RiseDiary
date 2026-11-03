package com.risediary.app.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.risediary.app.data.library.LibraryUsageRegistry
import com.risediary.app.media.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TimerLibraryStoreTest {
    private val id = "af461c43-d0b7-4b6a-92a6-100c6a892944"
    private val video = LocalVideoRef("content://com.risediary.app.dev.library/video/$id", "a.mp4", "video/mp4")
    private fun session() = TimerSession(status = TimerStatus.PAUSED, sessionId = "s", startedAtEpochMillis = 10_000L,
        elapsedMillis = 8000L, kind = TimerKind.VIDEO, video = VideoPlaybackSnapshot(video, 321L, 2f, true),
        videoSource = VideoSourceRef.LibraryVideo(id))
    private fun store(memory: Memory) = TimerSessionStore(memory,
        object : BootIdentityProvider { override fun currentBootCount() = 1 },
        object : ElapsedRealtimeClock { override fun millis() = 1000L },
        Clock.fixed(Instant.ofEpochMilli(20_000L), ZoneOffset.UTC))

    @Test fun persistedLibrarySessionRestoresAndPinsBeforeDeletionCanProceed() = runTest {
        val memory = Memory()
        store(memory).save(session())
        val usage = LibraryUsageRegistry()
        val holder = TimerStateHolder(usage)
        val restored = holder.restoreIfIdle { store(memory).load() }
        assertEquals(session(), restored)
        assertTrue(runCatching { usage.reserveDeletion(setOf(id)).close() }.isFailure)
        holder.set(TimerSession())
        usage.reserveDeletion(setOf(id)).close()
    }

    @Test fun oldPersistedVideoJsonNeedsNoDataResetAndKeepsPlayback() = runTest {
        val original = session().copy(videoSource = VideoSourceRef.DeviceVideo(video))
        val payload = JsonObject(Json.parseToJsonElement(Json.encodeToString(original)).jsonObject - "videoSource").toString()
        val memory = Memory(preferencesOf(stringPreferencesKey("session_json") to payload,
            stringPreferencesKey("status") to "PAUSED", intPreferencesKey("duration_policy_version") to 2))
        assertEquals(original, store(memory).load())
    }

    @Test fun corruptedLibrarySourceIsRejectedInsteadOfOpeningAnUnrelatedVideo() = runTest {
        val malformed = Json.encodeToString(session().copy(videoSource = VideoSourceRef.LibraryVideo("bad")))
        val memory = Memory(preferencesOf(stringPreferencesKey("session_json") to malformed,
            stringPreferencesKey("status") to "PAUSED", intPreferencesKey("duration_policy_version") to 2))
        assertTrue(runCatching { store(memory).load() }.isFailure)
    }

    private class Memory(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(data.value).also { data.value = it }
    }
}
