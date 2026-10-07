package com.risediary.app.data.backup

import org.junit.Assert.*
import org.junit.Test

class BackupIntegerTest {
    @Test fun rejectsOverflowThatWouldOtherwiseWrapToAValidRecordValue() {
        assertTrue(runCatching { requireBackupInt(4_294_967_297L) }.isFailure)
        assertTrue(runCatching { requireBackupInt(Int.MIN_VALUE.toLong()-1) }.isFailure)
        assertTrue(runCatching { requireBackupInt(1.5) }.isFailure)
    }
    @Test fun acceptsExactIntegerBoundsAndLongRepresentation() {
        assertEquals(Int.MAX_VALUE,requireBackupInt(Int.MAX_VALUE.toLong()))
        assertEquals(Int.MIN_VALUE,requireBackupInt(Int.MIN_VALUE))
        assertEquals(60,requireBackupInt(60L))
    }
}
