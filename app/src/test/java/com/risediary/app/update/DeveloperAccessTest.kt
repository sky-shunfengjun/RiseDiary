package com.risediary.app.update

import org.junit.Assert.*
import org.junit.Test

class DeveloperAccessTest {
    @Test fun twentiethTapOpensAndStartsANewSequence() {
        val taps = DeveloperTapCounter()
        repeat(19) { assertFalse(taps.tap(it * 100L)) }
        assertTrue(taps.tap(1900))
        assertFalse(taps.tap(2000))
    }
    @Test fun gapBoundaryAndLeavingRestartTheSequence() {
        val taps = DeveloperTapCounter()
        repeat(19) { taps.tap(it * 100L) }
        assertTrue(taps.tap(4800)) // Exactly 3 seconds still belongs to the sequence.
        repeat(19) { taps.tap(5000L + it * 100L) }
        assertFalse(taps.tap(9801))
        repeat(18) { assertFalse(taps.tap(9900L + it * 100L)) }
        taps.reset()
        assertFalse(taps.tap(12000))
    }
    @Test fun passwordRequiresExactMatch() {
        assertFalse(isDeveloperPasswordValid("109510287"))
        assertFalse(isDeveloperPasswordValid("10951028740"))
        assertFalse(isDeveloperPasswordValid(" 1095102874"))
        assertTrue(isDeveloperPasswordValid("1095102874"))
    }
}