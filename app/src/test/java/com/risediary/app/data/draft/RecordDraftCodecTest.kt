package com.risediary.app.data.draft

import com.risediary.app.media.LocalVideoRef
import com.risediary.app.ui.form.QuantityDraftSnapshot
import org.junit.Assert.*
import org.junit.Test

class RecordDraftCodecTest {
    private fun snapshot() = RecordDraftSnapshot("draft1", revision = 4L, sessionId = "timer1",
        startTime = 100_000L, endTime = 112_000L, durationSeconds = 8, timingSource = "timer",
        quantity = QuantityDraftSnapshot("estimated", 23, "9.", 80, true),
        video = LocalVideoRef("content://videos/document/1", "视频", "video/mp4"),
        distanceText = "2.", methodTags = listOf("手"), moodNote = "未填写完成")

    @Test fun restorePreservesBothQuantityInputsAndActualEndTime() {
        val restored = RecordDraftCodec.decode(RecordDraftCodec.encode(snapshot()))
        assertEquals("9.", restored.quantity.manualText)
        assertEquals(23, restored.quantity.estimatedTicks)
        assertEquals(112_000L, restored.endTime)
        assertEquals(8, restored.durationSeconds)
        assertEquals("content://videos/document/1", restored.video?.uriString)
        assertEquals(4L, restored.revision)
    }

    @Test fun zeroAmountAndPartialTextCanBeKeptBeforeSubmit() {
        val empty = snapshot().copy(quantity = QuantityDraftSnapshot("milliliters", 0, ".", 80, true))
        assertEquals(empty, RecordDraftCodec.decode(RecordDraftCodec.encode(empty)))
    }

    @Test fun corruptDraftDoesNotBecomeAnEmptyDraft() {
        assertTrue(runCatching { RecordDraftCodec.decode("{bad") }.isFailure)
        assertTrue(runCatching { RecordDraftCodec.encode(snapshot().copy(timingSource = "unknown")) }.isFailure)
    }

    @Test fun sliderOutsideItsSavedRangeIsRejected() {
        assertTrue(runCatching { RecordDraftCodec.encode(snapshot().copy(
            quantity = QuantityDraftSnapshot("estimated", 81, "", 80, true))) }.isFailure)
    }
}
