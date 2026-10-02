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
import com.risediary.app.data.HomeCardOrderPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
class CardOrderViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {
    data class CardDef(val id: String, @StringRes val labelRes: Int)
    private data class LayoutSnapshot(val order: String, val visibility: String)

    val allCards = listOf(
        CardDef("checkin", R.string.card_order_checkin),
        CardDef("overview", R.string.card_order_overview),
        CardDef("trend", R.string.card_order_trend),
        CardDef("length", R.string.card_order_length),
        CardDef("achievement", R.string.card_order_achievement),
    )

    val orderedIds = mutableStateListOf<String>()
    var visibility by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set
    var loadErrorMessage by mutableStateOf<String?>(null)
        private set
    private var loadedLayout: LayoutSnapshot? = null
    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadedLayout = null
        loadErrorMessage = null
        loadJob = viewModelScope.launch {
            try {
                val layout = readLayout()
                val ids = allCards.map { it.id }
                val merged = HomeCardOrderPolicy.normalizeOrder(layout.order, ids)
                val storedVisibility = runCatching {
                    HomeCardOrderPolicy.readVisibility(layout.visibility)
                }.getOrDefault(emptyMap())
                orderedIds.clear()
                orderedIds.addAll(merged)
                visibility = ids.associateWith { storedVisibility[it] ?: true }
                loadedLayout = layout
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                orderedIds.clear()
                orderedIds.addAll(allCards.map { it.id })
                visibility = allCards.associate { it.id to true }
                loadErrorMessage = "无法读取卡片布局，请重新读取后再保存"
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

    /** The same permit protects the baseline check and the atomic two-field commit. */
    suspend fun save() = prefs.maintenanceGate.write {
        val expected = loadedLayout ?: error("卡片布局尚未读取成功，请重新读取后再保存")
        prefs.maintenanceGate.requireCurrent(expected, readLayout())
        val ids = allCards.map { it.id }
        val orderJson = JsonArray(HomeCardOrderPolicy.normalizeIds(orderedIds.toList(), ids).map(::JsonPrimitive)).toString()
        val visibilityJson = JsonObject(visibility.mapValues { JsonPrimitive(it.value) }).toString()
        prefs.setHomeCardLayout(orderJson, visibilityJson)
        // A later save from this page compares with its own last successful commit.
        loadedLayout = LayoutSnapshot(orderJson, visibilityJson)
    }

    private suspend fun readLayout(): LayoutSnapshot {
        val settings = prefs.settingsSnapshot(prefs.rawSnapshot())
        return LayoutSnapshot(settings.homeCardOrder, settings.homeCardVisibility)
    }

    fun getLabelRes(cardId: String): Int = allCards.find { it.id == cardId }?.labelRes ?: 0
}