package com.risediary.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerMathTest {
    @Test
    fun runningSessionUsesMonotonicClock() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = 2_000L,
            resumedAtElapsedRealtime = 10_000L,
            resumedAtWallClock = 100_000L
        )

        assertEquals(5_000L, TimerMath.elapsed(session, 13_000L, 200_000L))
    }

    @Test
    fun rebootFallsBackToWallClock() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = 2_000L,
            resumedAtElapsedRealtime = 50_000L,
            resumedAtWallClock = 100_000L
        )

        assertEquals(7_000L, TimerMath.elapsed(session, 1_000L, 105_000L))
    }

    @Test
    fun elapsedTimeIsCappedAtOneHundredTwentyMinutes() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = TimerMath.MAX_DURATION_MILLIS - 1_000L,
            resumedAtElapsedRealtime = 0L,
            resumedAtWallClock = 0L
        )

        assertEquals(
            TimerMath.MAX_DURATION_MILLIS,
            TimerMath.elapsed(session, 10_000L, 10_000L)
        )
    }

    @Test
    fun milestoneMaskTracksThirtyMinuteIntervalsWithoutDuplicates() {
        val thirtyMask = TimerMilestones.maskThrough(30L * 60L * 1_000L)
        val ninetyMask = TimerMilestones.maskThrough(90L * 60L * 1_000L)

        assertEquals(TimerMilestone.THIRTY.bit, thirtyMask)
        assertEquals(
            TimerMilestone.THIRTY.bit or
                TimerMilestone.SIXTY.bit or
                TimerMilestone.NINETY.bit,
            ninetyMask
        )
        assertEquals(
            null,
            TimerMilestones.latestUnnotified(
                elapsedMillis = 90L * 60L * 1_000L,
                notifiedMask = ninetyMask
            )
        )
    }

    @Test
    fun latestMilestoneWinsAfterAClockJump() {
        assertEquals(
            TimerMilestone.NINETY,
            TimerMilestones.latestUnnotified(
                elapsedMillis = 95L * 60L * 1_000L,
                notifiedMask = 0
            )
        )
        assertEquals(
            TimerMilestone.LIMIT,
            TimerMilestones.latestUnnotified(
                elapsedMillis = TimerMath.MAX_DURATION_MILLIS,
                notifiedMask = 0
            )
        )
    }

    @Test
    fun advanceMovesBaselineSoPeriodicTicksDoNotCompoundElapsedTime() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = 0L,
            resumedAtElapsedRealtime = 1_000L,
            resumedAtWallClock = 10_000L
        )

        val firstTick = TimerMath.advance(session, 1_050L, 10_050L)
        val secondTick = TimerMath.advance(firstTick, 1_100L, 10_100L)

        assertEquals(50L, firstTick.elapsedMillis)
        assertEquals(100L, secondTick.elapsedMillis)
    }
}
