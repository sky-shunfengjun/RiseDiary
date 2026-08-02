package com.risediary.app.data.repository

import org.json.JSONArray

object TagJson {
    fun encode(tags: Collection<String>): String {
        val array = JSONArray()
        tags.forEach(array::put)
        return array.toString()
    }

    fun decode(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        buildList(array.length()) {
            repeat(array.length()) { index ->
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }.getOrDefault(emptyList())
}
