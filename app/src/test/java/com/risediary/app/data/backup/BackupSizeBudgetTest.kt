package com.risediary.app.data.backup

import org.junit.Assert.*
import org.junit.Test

class BackupSizeBudgetTest {
    @Test fun exactEntryLimitWorksButOneMoreUncompressedByteIsRejected() {
        val budget=BackupSizeBudget(); budget.beginEntry()
        repeat(32) { budget.account(1024*1024) }
        assertThrows(IllegalArgumentException::class.java) { budget.account(1) }
    }
    @Test fun entryResetCannotEvadeTotalLimit() {
        val budget=BackupSizeBudget()
        repeat(4) { budget.beginEntry(); repeat(32) { budget.account(1024*1024) } }
        budget.beginEntry()
        assertThrows(IllegalArgumentException::class.java) { budget.account(1) }
    }
    @Test fun originalFiveMiBLimitDoesNotRejectValidLargerEntry() {
        val budget=BackupSizeBudget(); budget.beginEntry(); repeat(7) { budget.account(1024*1024) }
    }
    @Test fun reserveIsInAdditionToPendingWrites() {
        requireBackupSpace(48L*1024*1024,32L*1024*1024)
        assertThrows(IllegalArgumentException::class.java) { requireBackupSpace(48L*1024*1024-1,32L*1024*1024) }
    }
}
