package com.risediary.app.service

import com.risediary.app.data.DataMaintenanceGate
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.library.LibraryUsageRegistry
import com.risediary.app.media.*
import com.risediary.app.ui.form.RecordFormSessionStore
import com.risediary.app.ui.navigation3.Route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TimerLibrarySourceTest {
    @Test fun twentyFourHourLimitKeepsTheMaterialAndPlaybackWhenHandedToTheForm() {
        val reached = prepareTimerTransition(TimerMath.advance(running(), 86_400_500L, 86_410_000L)).session
        assertEquals(TimerStatus.LIMIT_REACHED, reached.status)
        val form = RecordFormSessionStore().createFromTimer(reached, 80)
        assertEquals(86_400, form.durationSeconds)
        assertEquals(VideoSourceRef.LibraryVideo(id), form.videoSource)
        assertEquals(video, form.video)
        assertEquals(4321L, reached.video!!.positionMillis)
    }
    private val id = "af461c43-d0b7-4b6a-92a6-100c6a892944"
    private val video = LocalVideoRef("content://com.risediary.app.dev.library/video/$id", "素材.mp4", "video/mp4")
    private fun flight() = Flight(startTime = 10_000L, endTime = 18_000L, durationSeconds = 8,
        spurtCount = null, semenVolumeMl = 1f, ejaculationDistanceCm = null, methodTags = "[]", moodNote = "")
    private fun request(source: VideoSourceRef = VideoSourceRef.LibraryVideo(id)) = TimerStartRequest(
        "session", TimerKind.VIDEO, VideoPlaybackSnapshot(video, 4321L, 1.5f, true), 10_000L, 500L, source)
    private fun running() = TimerSessionPolicy.start(TimerSession(), request(), 10_100L, 600L, 1)

    @Test fun libraryIdentitySurvivesJsonAndNotificationReturnsToTheLibraryPlayer() {
        val session = Json.decodeFromString<TimerSession>(Json.encodeToString(running()))
        assertEquals(VideoSourceRef.LibraryVideo(id), session.videoSource)
        assertEquals(Route.LibraryViewer(id, "session"), timerDestination(session))
        assertEquals(4321L, session.video!!.positionMillis)
        assertEquals(1.5f, session.video.speed)
        assertTrue(session.video.loop)
    }

    @Test fun oldSnapshotWithoutSourceDecodesAsPhoneVideo() {
        val original = running().copy(videoSource = VideoSourceRef.DeviceVideo(video))
        val oldJson = JsonObject(Json.parseToJsonElement(Json.encodeToString(original)).jsonObject - "videoSource")
        val restored = Json.decodeFromString<TimerSession>(oldJson.toString())
        assertEquals(VideoSourceRef.DeviceVideo(video), restored.videoSource)
        assertEquals(Route.VideoTimer("session"), timerDestination(restored))
    }

    @Test fun malformedOrMismatchedSourceCannotStart() {
        for (source in listOf(VideoSourceRef.LibraryVideo("bad"), VideoSourceRef.DeviceVideo(video.copy(displayName = "other")))) {
            assertTrue(runCatching { TimerSessionPolicy.start(TimerSession(), request(source), 10_100L, 600L, 1) }.isFailure)
        }
        assertTrue(runCatching { TimerSessionPolicy.start(TimerSession(), request().copy(kind = TimerKind.NORMAL), 10_100L, 600L, 1) }.isFailure)
    }

    @Test fun timerToUnsavedFormKeepsTheMaterialProtectedUntilTheFormIsDiscarded() {
        val usage = LibraryUsageRegistry()
        val holder = TimerStateHolder(usage)
        val forms = RecordFormSessionStore(DataMaintenanceGate(), usage)
        holder.set(running())
        assertTrue(runCatching { usage.reserveDeletion(setOf(id)).close() }.isFailure)
        val form = forms.createFromTimer(running().copy(status = TimerStatus.FINISHED, elapsedMillis = 8000L, endedAtEpochMillis = 18_000L), 80)
        holder.set(TimerSession())
        assertEquals(VideoSourceRef.LibraryVideo(id), form.videoSource)
        assertTrue(forms.videoUris().isEmpty())
        assertTrue(runCatching { usage.reserveDeletion(setOf(id)).close() }.isFailure)
        val linked = flight().withVideoAssociation(form.video, form.videoSource, null)
        assertEquals(id, linked.materialId)
        assertEquals("素材.mp4", linked.materialName)
        assertNull(linked.videoUri)
        forms.discard(form.formId)
        usage.reserveDeletion(setOf(id)).close()
    }

    @Test fun editingAndReplacingMaterialLinksClearsTheOppositeSourceFields() {
        val linked = flight().withVideoAssociation(video, VideoSourceRef.LibraryVideo(id), null)
        assertEquals(VideoSourceRef.LibraryVideo(id), linked.videoSourceRef())
        val phone = LocalVideoRef("content://phone/1", "手机.mp4", "video/mp4")
        val replaced = linked.withVideoAssociation(phone, VideoSourceRef.DeviceVideo(phone), null)
        assertNull(replaced.materialId)
        assertNull(replaced.materialName)
        assertEquals(phone.uriString, replaced.videoUri)
        val removed = linked.withVideoAssociation(null, null, "原素材.mp4")
        assertTrue(removed.materialRemoved)
        assertEquals("原素材.mp4", removed.materialName)
        assertNull(removed.videoSourceRef())
    }
}
