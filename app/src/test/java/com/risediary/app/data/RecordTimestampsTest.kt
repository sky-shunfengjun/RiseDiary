package com.risediary.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordTimestampsTest {
    @Test fun editingAfterAClockRollbackDoesNotPredateCreation() {
        assertEquals(20_000L, RecordTimestamps.updatedAt(20_000L, 20_000L, 10_000L))
    }
    @Test fun laterEditsDoNotLoseThePreviousModificationTimeWhenClockMovesBack() {
        assertEquals(30_000L, RecordTimestamps.updatedAt(20_000L, 30_000L, 25_000L))
    }
    @Test fun anExistingInvalidModificationTimeIsRepairedWithoutChangingCreation() {
        assertEquals(20_000L, RecordTimestamps.updatedAt(20_000L, 5_000L, 10_000L))
    }
    @Test fun aForwardMovingClockKeepsTheActualEditTime() {
        assertEquals(40_000L, RecordTimestamps.updatedAt(20_000L, 30_000L, 40_000L))
    }
}