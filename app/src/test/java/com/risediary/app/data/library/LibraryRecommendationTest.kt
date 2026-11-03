package com.risediary.app.data.library

import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibraryRecommendationTest {
    private val day = LocalDate.of(2026, 11, 3)
    private fun asset(n: Int, state: String = "ready", type: String = "video") = LibraryAsset(
        "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}", null, type, "$n.mp4", "$n.mp4", "video/mp4", 1L, 1000L, "hash$n", 0L, n.toLong(), state)

    @Test fun sameDayKeepsThePersistedChoiceAcrossImportAndReordering() = runTest {
        var saved: LibraryRecommendationChoice? = null
        var writes = 0
        val picker = LibraryRecommender({ saved }, { saved = it; writes++ }, { true }, { 0 })
        val first = picker.pick(day, listOf(asset(1), asset(2)))!!
        assertEquals(first.id, picker.pick(day, listOf(asset(3), asset(2), asset(1)))?.id)
        assertEquals(1, writes)
    }
    @Test fun nextDayAvoidsTheLastVideoWhenThereAreAlternatives() = runTest {
        var saved: LibraryRecommendationChoice? = LibraryRecommendationChoice(day.toString(), asset(1).id)
        val picker = LibraryRecommender({ saved }, { saved = it }, { true }, { 0 })
        assertEquals(asset(2).id, picker.pick(day.plusDays(1), listOf(asset(1), asset(2)))?.id)
        assertEquals(day.plusDays(1).toString(), saved?.day)
    }
    @Test fun deletedUnreadableAndNonVideoChoicesAreNotRecommended() = runTest {
        var saved: LibraryRecommendationChoice? = LibraryRecommendationChoice(day.toString(), asset(1).id)
        val picker = LibraryRecommender({ saved }, { saved = it }, { it.id != asset(1).id }, { 0 })
        val all = listOf(asset(1), asset(2, "deleting"), asset(3, type = "image"), asset(4))
        assertEquals(asset(4).id, picker.pick(day, all)?.id)
        assertEquals(asset(4).id, picker.pick(day, listOf(asset(4)))?.id)
    }
    @Test fun emptyLibraryDoesNotEraseThePreviousChoiceAndSingleVideoCanRepeat() = runTest {
        var saved: LibraryRecommendationChoice? = LibraryRecommendationChoice(day.toString(), asset(1).id)
        val picker = LibraryRecommender({ saved }, { saved = it }, { true }, { 0 })
        assertNull(picker.pick(day.plusDays(1), emptyList()))
        assertEquals(day.toString(), saved?.day)
        assertEquals(asset(1).id, picker.pick(day.plusDays(1), listOf(asset(1)))?.id)
    }
    @Test fun concurrentReadsPublishOnlyOneChoice() = runTest {
        var saved: LibraryRecommendationChoice? = null
        var writes = 0
        val picker = LibraryRecommender({ saved }, { kotlinx.coroutines.yield(); saved = it; writes++ }, { true }, { 0 })
        val a = async { picker.pick(day, listOf(asset(1), asset(2))) }
        val b = async { picker.pick(day, listOf(asset(2), asset(1))) }
        assertEquals(a.await()?.id, b.await()?.id)
        assertEquals(1, writes)
    }
    @Test fun persistenceFailureIsReportedWithoutReturningAnUnstableRecommendation() = runTest {
        val picker = LibraryRecommender({ null }, { error("disk full") }, { true }, { 0 })
        assertTrue(runCatching { picker.pick(day, listOf(asset(1))) }.isFailure)
    }
}
