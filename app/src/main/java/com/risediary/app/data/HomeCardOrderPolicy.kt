package com.risediary.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Shared compatibility boundary for display, ordering and backup import. */
object HomeCardOrderPolicy {
    val currentIds = listOf("checkin", "overview", "trend", "length", "achievement")
    private val legacyIds = mapOf(
        "recent7" to "checkin", "summary" to "checkin",
        "heatmap" to "checkin", "distance" to "trend"
    )

    fun normalizeIds(ids: List<String>, allIds: List<String>): List<String> {
        val available = allIds.distinct()
        val normalized = ids.map { legacyIds[it] ?: it }.distinct().filter { it in available }
        return normalized + available.filterNot(normalized::contains)
    }

    /** Rendering is defensive; unknown future IDs remain in storage but have no row. */
    fun normalizeOrder(orderJson: String, allIds: List<String>): List<String> =
        normalizeIds(runCatching { readOrder(orderJson) }.getOrDefault(emptyList()), allIds)

    /** Import is strict about types, while retaining unknown future IDs. */
    fun normalizeStoredOrder(orderJson: String): String = JsonArray(
        readOrder(orderJson).map { legacyIds[it] ?: it }.distinct().map(::JsonPrimitive)
    ).toString()

    fun validateVisibility(visibilityJson: String) {
        readVisibility(visibilityJson)
    }

    fun readVisibility(visibilityJson: String): Map<String, Boolean> {
        val values = Json.parseToJsonElement(visibilityJson)
        require(values is JsonObject) { "首页卡片显示设置必须为对象" }
        return values.mapValues { (_, value) ->
            require(value is JsonPrimitive && !value.isString && value.booleanOrNull != null) {
                "首页卡片显示设置必须为布尔值"
            }
            value.booleanOrNull!!
        }
    }

    fun visibleOrder(orderJson: String, visibilityJson: String, allIds: List<String>): List<String> {
        val visibility = runCatching { readVisibility(visibilityJson) }.getOrDefault(emptyMap())
        return normalizeOrder(orderJson, allIds).filter { visibility[it] ?: true }
    }

    private fun readOrder(json: String): List<String> {
        val values = Json.parseToJsonElement(json)
        require(values is JsonArray) { "首页卡片排序必须为数组" }
        return values.map { value ->
            require(value is JsonPrimitive && value.isString) { "首页卡片排序项必须为文字" }
            value.content
        }
    }
}
