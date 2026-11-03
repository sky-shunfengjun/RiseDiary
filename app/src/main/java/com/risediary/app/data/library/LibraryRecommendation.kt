package com.risediary.app.data.library

import com.risediary.app.data.UserPreferences
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class LibraryRecommendationChoice(val day: String, val id: String) {
    init { require(LocalDate.parse(day).toString() == day); require(UUID.fromString(id).toString() == id) }
}

/** Persist before presenting a new choice; concurrent collectors share the same daily pick. */
@Singleton
class LibraryRecommender internal constructor(private val read: suspend () -> LibraryRecommendationChoice?,
    private val write: suspend (LibraryRecommendationChoice) -> Unit,
    private val available: suspend (LibraryAsset) -> Boolean, private val randomIndex: (Int) -> Int) {
    @Inject constructor(preferences: UserPreferences, storage: LibraryStorage) : this(
        { preferences.libraryRecommendation.first() }, { preferences.setLibraryRecommendation(it) },
        { asset -> withContext(Dispatchers.IO) { storage.files.media(asset.id).let { it.isFile && it.canRead() && it.length() == asset.sizeBytes } } },
        { Random.nextInt(it) })
    private val mutex = Mutex()
    suspend fun pick(day: LocalDate, assets: List<LibraryAsset>): LibraryAsset? = mutex.withLock {
        val previous = read()
        val candidates = assets.filter { it.state == "ready" && it.mediaType == "video" }.sortedBy { it.id }
        if (previous?.day == day.toString()) candidates.firstOrNull { it.id == previous.id }?.let {
            if (available(it)) return@withLock it
        }
        val usable = candidates.filter { available(it) }
        if (usable.isEmpty()) return@withLock null
        val alternatives = usable.filter { it.id != previous?.id }.ifEmpty { usable }
        val selected = alternatives[randomIndex(alternatives.size).coerceIn(alternatives.indices)]
        val next = LibraryRecommendationChoice(day.toString(), selected.id)
        if (next != previous) write(next)
        selected
    }
}
