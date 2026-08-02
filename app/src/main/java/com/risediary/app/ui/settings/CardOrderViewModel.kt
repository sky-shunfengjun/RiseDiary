package com.risediary.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    data class CardDef(val id: String, val label: String)

    val allCards = listOf(
        CardDef("checkin", "打卡"),
        CardDef("overview", "数据概览"),
        CardDef("trend", "趋势图"),
        CardDef("length", "长度追踪"),
        CardDef("achievement", "成就"),
    )

    /** Observable ordered list of card IDs (Compose State). */
    val orderedIds = mutableStateListOf<String>()

    /** Observable visibility map (Compose State). */
    var visibility by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
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

            orderedIds.clear()
            orderedIds.addAll(merged.ifEmpty { allCards.map { it.id } })

            // Load visibility from DataStore
            val visJson = prefs.homeCardVisibility.first()
            visibility = try {
                val obj = JSONObject(visJson)
                allCards.associate { card ->
                    card.id to obj.optBoolean(card.id, true)
                }
            } catch (_: Exception) {
                allCards.associate { it.id to true }
            }
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
        val orderJson = JSONArray().apply {
            orderedIds.forEach { put(it) }
        }.toString()
        prefs.setHomeCardOrder(orderJson)

        val visJson = JSONObject().apply {
            visibility.forEach { (k, v) -> put(k, v) }
        }.toString()
        prefs.setHomeCardVisibility(visJson)
    }

    fun getLabel(cardId: String): String = allCards.find { it.id == cardId }?.label ?: cardId
}
