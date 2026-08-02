package com.risediary.app.data

import com.risediary.app.data.dao.TagDao
import com.risediary.app.data.entity.Tag
import kotlinx.coroutines.flow.first

/**
 * Initializes default data on first app launch.
 * Called from RiseDiaryApp.onCreate().
 */
object SeedData {

    val defaultTags = listOf(
        Tag(name = "手动", color = "#FF9800", sortOrder = 0),
        Tag(name = "玩具", color = "#9C27B0", sortOrder = 1),
        Tag(name = "口交", color = "#E91E63", sortOrder = 2),
        Tag(name = "其他", color = "#607D8B", sortOrder = 3),
    )

    suspend fun initializeIfNeeded(tagDao: TagDao, preferences: UserPreferences) {
        val initialized = preferences.defaultTagsInitialized.first()
        val onboardingCompleted = preferences.onboardingCompleted.first()
        val tagCount = tagDao.count()
        if (shouldSeedDefaultTags(initialized, onboardingCompleted, tagCount)) {
            defaultTags.forEach { tagDao.insert(it) }
        }
        if (!initialized) preferences.markDefaultTagsInitialized()
    }
}

internal fun shouldSeedDefaultTags(
    initialized: Boolean,
    onboardingCompleted: Boolean,
    tagCount: Int
): Boolean = !initialized && !onboardingCompleted && tagCount == 0
