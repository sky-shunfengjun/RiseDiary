package com.risediary.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.risediary.app.R
import com.risediary.app.data.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class CardOrderViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {

    data class CardDef(val id: String, @StringRes val labelRes: Int)

    val allCards = listOf(
        CardDef("checkin", R.string.card_order_checkin),
        CardDef("overview", R.string.card_order_overview),
        CardDef("trend", R.string.card_order_trend),
        CardDef("length", R.string.card_order_length),
        CardDef("achievement", R.string.card_order_achievement),
    )

    /** Observable ordered list of card IDs (Compose State). */
    val orderedIds = mutableStateListOf<String>()

    /** Observable visibility map (Compose State). */
    var visibility by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    private var loadFailed = false
    private var loaded = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val result = runCatching {
                // Load order from DataStore
                val orderJson = prefs.homeCardOrder.first()
                val savedIds: List<String> = try {
                    val arr = JSONArray(orderJson)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) {
                    emptyList()
                }

                // Merge: saved order first, then any new cards not in saved
                val savedSet = savedIds.toSet()
                val merged = savedIds + allCards.map { it.id }.filter { it !in savedSet }

                // Load visibility from DataStore
                val visJson = prefs.homeCardVisibility.first()
                val visMap = try {
                    val obj = JSONObject(visJson)
                    allCards.associate { card ->
                        card.id to obj.optBoolean(card.id, true)
                    }
                } catch (_: Exception) {
                    allCards.associate { it.id to true }
                }
                Triple(merged, visMap, false)
            }.getOrElse {
                // DataStore unreadable: fall back to defaults and refuse to overwrite
                // whatever was persisted with an accidental empty list.
                Triple(
                    allCards.map { it.id },
                    allCards.associate { it.id to true },
                    true
                )
            }
            loadFailed = result.third
            orderedIds.clear()
            orderedIds.addAll(result.first.ifEmpty { allCards.map { it.id } })
            visibility = result.second
            loaded = true
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in orderedIds.indices || toIndex !in orderedIds.indices) return
        val item = orderedIds.removeAt(fromIndex)
        orderedIds.add(toIndex, item)
    }

    fun toggleVisible(cardId: String) {
        val current = visibility[cardId] ?: true
        visibility = visibility + (cardId to !current)
    }

    /** Save current order and visibility to DataStore. Suspends until write completes. */
    suspend fun save() {
        // Never overwrite persisted order with the empty in-memory defaults if the
        // initial load has not finished (or failed) yet.
        if (!loaded || loadFailed) return
        val orderJson = JSONArray().apply {
            orderedIds.forEach { put(it) }
        }.toString()
        prefs.setHomeCardOrder(orderJson)

        val visJson = JSONObject().apply {
            visibility.forEach { (k, v) -> put(k, v) }
        }.toString()
        prefs.setHomeCardVisibility(visJson)
    }

    fun getLabelRes(cardId: String): Int = allCards.find { it.id == cardId }?.labelRes ?: 0
}
