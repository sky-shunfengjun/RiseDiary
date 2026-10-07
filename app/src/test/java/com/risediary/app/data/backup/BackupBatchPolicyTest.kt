package com.risediary.app.data.backup

import org.junit.Assert.*
import org.junit.Test

class BackupBatchPolicyTest {
    @Test fun longNotesReducePageBeforeRowsAreAllocated() {
        assertEquals(2,backupBatchSize(listOf(400_000,400_000,400_000)))
        assertEquals(1,backupBatchSize(listOf(2_000_000,20)))
        assertEquals(500,backupBatchSize(List(800) { 10 }))
        assertEquals(0,backupBatchSize(emptyList()))
    }
    @Test fun invalidSizeCannotSilentlyDisableLimits() {
        assertThrows(IllegalArgumentException::class.java) { backupBatchSize(listOf(-1)) }
    }
}
