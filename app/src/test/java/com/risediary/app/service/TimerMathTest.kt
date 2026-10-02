package com.risediary.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerMathTest {
    @Test
    fun unknownBootIdentityDoesNotAddNewBootUptime() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = 5 * 60_000L,
            resumedAtElapsedRealtime = 10 * 60_000L
        )
        assertEquals(5 * 60_000L, TimerMath.elapsed(session, 20 * 60_000L, 1_000_000L))
    }

    @Test fun newerUptimeAfterRebootRestoresPausedAtSavedDuration() {
        val session = TimerSession(TimerStatus.RUNNING, elapsedMillis = 300_000L,
            resumedAtElapsedRealtime = 600_000L, bootCount = 4)
        val restored = TimerMath.restore(session, 1_200_000L, 9_999_999L, 5)
        assertEquals(TimerStatus.PAUSED, restored.status)
        assertEquals(300_000L, restored.elapsedMillis)
        assertEquals(0L, restored.resumedAtElapsedRealtime)
    }

    @Test fun sameBootProcessRecreationAdvancesAndMovesBaseline() {
        val session = TimerSession(TimerStatus.RUNNING, elapsedMillis = 300_000L,
            resumedAtElapsedRealtime = 600_000L, bootCount = 4)
        val restored = TimerMath.restore(session, 1_200_000L, 9_999_999L, 4)
        assertEquals(TimerStatus.RUNNING, restored.status)
        assertEquals(900_000L, restored.elapsedMillis)
        assertEquals(1_200_000L, restored.resumedAtElapsedRealtime)
    }

    @Test fun missingOrUnreadableBootIdentityAlwaysRestoresPaused() {
        for (savedBoot in listOf(null, 4)) for (currentBoot in listOf<Int?>(null, 5)) {
            val session = TimerSession(TimerStatus.RUNNING, elapsedMillis = 300_000L,
                resumedAtElapsedRealtime = 600_000L, bootCount = savedBoot)
            val restored = TimerMath.restore(session, 1_200_000L, 9_999_999L, currentBoot)
            assertEquals(TimerStatus.PAUSED, restored.status)
            assertEquals(300_000L, restored.elapsedMillis)
        }
    }

    @Test fun terminalAndPausedSessionsAreNotRestartedOnRestore() {
        for (status in listOf(TimerStatus.PAUSED, TimerStatus.FINISHED, TimerStatus.LIMIT_REACHED)) {
            val session = TimerSession(status, elapsedMillis = 120_000L, bootCount = 4)
            assertEquals(session, TimerMath.restore(session, 1_200_000L, 9_999_999L, 5))
        }
    }
    @Test
    fun runningSessionUsesMonotonicClock() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = 2_000L,
            resumedAtElapsedRealtime = 10_000L,
            resumedAtWallClock = 100_000L,
            bootCount = 1
        )

        assertEquals(5_000L, TimerMath.elapsed(session, 13_000L, 200_000L, 1))
    }

    @Test
    fun rebootFreezesElapsedInsteadOfCountingPoweredOffTime() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = 2_000L,
            resumedAtElapsedRealtime = 50_000L,
            resumedAtWallClock = 100_000L,
            bootCount = 1
        )

        // elapsedRealtime reset after reboot: delta is negative and must not
        // fall back to wall clock (which would add the powered-off interval).
        assertEquals(2_000L, TimerMath.elapsed(session, 1_000L, 105_000L, 1))
    }

    @Test
    fun elapsedTimeIsCappedAtOneHundredTwentyMinutes() {
        val session = TimerSession(
            status = TimerStatus.RUNNING,
            elapsedMillis = TimerMath.MAX_DURATION_MILLIS - 1_000L,
            resumedAtElapsedRealtime = 0L,
            resumedAtWallClock = 0L,
            bootCount = 1
        )

        assertEquals(
            TimerMath.MAX_DURATION_MILLIS,
            TimerMath.elapsed(session, 10_000L, 10_000L, 1)
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
