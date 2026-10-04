package com.risediary.app.util

import org.junit.Assert.*
import org.junit.Test

class RecordTimingPolicyTest {
    @Test fun timerCanHaveAPauseButManualTimingMustMatch() {
        assertNull(RecordTimingPolicy.validate(100_000L, 112_000L, 8, "timer"))
        assertNotNull(RecordTimingPolicy.validate(100_000L, 112_000L, 8, "manual"))
    }
    @Test fun timerCannotExceedActualTimeOrDurationLimit() {
        assertNotNull(RecordTimingPolicy.validate(100_000L, 112_000L, 14, "timer"))
        assertNotNull(RecordTimingPolicy.validate(100_000L, 100_000L + 86_401_000L, 86401, "timer"))
        assertNull(RecordTimingPolicy.validate(100_000L, 100_000L + 86_404_000L, 86400, "timer"))
    }
    @Test fun unknownSourceAndNegativeSpanAreRejected() {
        assertNotNull(RecordTimingPolicy.validate(100_000L, 108_000L, 8, "guessed"))
        assertNotNull(RecordTimingPolicy.validate(100_000L, 99_000L, 8, "timer"))
    }
}
