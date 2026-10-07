package com.risediary.app.data.repository

import org.json.JSONArray

object TagJson {
    /** No artificial count limit: backup byte budgets bound large collections. */
    fun validate(json: String) {
        // Validate one tag at a time; do not materialize another huge JSONArray on export/import.
        val reader = org.json.JSONTokener(json)
        require(reader.nextClean() == '[') { "标签数据无效" }
        var next = reader.nextClean()
        if (next != ']') {
            reader.back()
            while (true) {
                val value = reader.nextValue()
                require(value is String) { "标签数据无效" }
                require(value.trim().length in 1..20) { "标签名称无效" }
                next = reader.nextClean()
                if (next == ']') break
                require(next == ',') { "标签数据无效" }
                next = reader.nextClean()
                require(next != ']' && next != 0.toChar()) { "标签数据无效" }
                reader.back()
            }
        }
        require(reader.nextClean() == 0.toChar()) { "标签数据无效" }
    }

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
