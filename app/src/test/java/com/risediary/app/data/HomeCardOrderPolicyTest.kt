package com.risediary.app.data

import org.junit.Assert.*
import org.junit.Test

class HomeCardOrderPolicyTest {
    private val ids = listOf("checkin", "overview", "trend", "length", "achievement")

    @Test fun duplicateAndLegacyIdsRenderOnlyOneCurrentCard() {
        assertEquals(ids, HomeCardOrderPolicy.normalizeOrder(
            "[\"checkin\",\"checkin\",\"heatmap\",\"overview\",\"distance\"]", ids
        ))
    }

    @Test fun unknownIdsSurviveStoredNormalizationButAreNotRendered() {
        val json = HomeCardOrderPolicy.normalizeStoredOrder("[\"future\",\"distance\",\"future\",\"trend\"]")
        assertEquals("[\"future\",\"trend\"]", json)
        assertEquals(listOf("trend", "checkin", "overview", "length", "achievement"),
            HomeCardOrderPolicy.normalizeOrder(json, ids))
    }

    @Test fun nonStringAndNonBooleanBackupValuesAreRejected() {
        assertTrue(runCatching { HomeCardOrderPolicy.normalizeStoredOrder("[1,\"checkin\"]") }.isFailure)
        assertTrue(runCatching { HomeCardOrderPolicy.normalizeStoredOrder("{\"checkin\":true}") }.isFailure)
        assertTrue(runCatching { HomeCardOrderPolicy.validateVisibility("{\"checkin\":\"false\"}") }.isFailure)
        assertTrue(runCatching { HomeCardOrderPolicy.validateVisibility("{\"checkin\":0}") }.isFailure)
    }

    @Test fun emptyMalformedAndHiddenCardsAreHandledWithoutDuplicateKeys() {
        assertEquals(ids, HomeCardOrderPolicy.normalizeOrder("[]", ids))
        assertEquals(ids, HomeCardOrderPolicy.normalizeOrder("not-json", ids))
        assertEquals(listOf("overview", "trend", "length", "achievement"),
            HomeCardOrderPolicy.visibleOrder("[\"heatmap\",\"checkin\"]", "{\"checkin\":false}", ids))
    }
}
