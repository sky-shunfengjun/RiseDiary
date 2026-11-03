package com.risediary.app.data.backup

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LibraryRecordBackupTest {
    @Test fun legacyRowsDefaultToNoMaterial() {
        val row = BackupJsonCodec.parseFlights(JSONArray().put(legacy()).toString()).single()
        assertNull(row.materialId)
        assertNull(row.materialName)
        assertFalse(row.materialRemoved)
    }

    @Test fun removedMaterialKeepsNameAndOtherRecordData() {
        val input = legacy().put("materialName", "已删除的视频").put("materialRemoved", true)
        val row = BackupJsonCodec.parseFlights(JSONArray().put(input).toString()).single()
        val restored = BackupJsonCodec.parseFlights(BackupJsonCodec.flightsToJson(listOf(row)).toString()).single()
        assertEquals(row, restored)
        assertTrue(restored.materialRemoved)
        assertNull(restored.materialId)
        assertEquals(input.getString("moodNote"), restored.moodNote)
    }

    @Test fun invalidIdentityAndConflictingSourcesAreRejected() {
        val id = "11111111-1111-4111-8111-111111111111"
        val rows = listOf(
            legacy().put("materialId", "bad").put("materialName", "视频"),
            legacy().put("materialId", id),
            legacy().put("materialRemoved", "true"),
            legacy().put("materialId", id).put("materialName", "视频").put("materialRemoved", true),
            legacy().put("materialId", id).put("materialName", "视频")
                .put("videoUri", "content://videos/document/1").put("videoDisplayName", "phone.mp4")
        )
        rows.forEach { input -> assertThrows(IllegalArgumentException::class.java) {
            BackupJsonCodec.parseFlights(JSONArray().put(input).toString())
        } }
    }

    private fun legacy() = JSONArray(checkNotNull(javaClass.getResourceAsStream("/backup-v1/flights.json"))
        .bufferedReader().use { it.readText() }).getJSONObject(0)

    @Test fun materialIdentitySurvivesDiaryBackupWithoutVideoBytesOrDevicePaths() {
        val id = "11111111-1111-4111-8111-111111111111"
        val input = legacy().put("materialId", id).put("materialName", "测试素材.mp4")
        val restored = BackupJsonCodec.parseFlights(JSONArray().put(input).toString()).single()
        val output = BackupJsonCodec.flightToJson(restored)
        assertEquals(id, output.optString("materialId"))
        assertEquals("测试素材.mp4", output.optString("materialName"))
        assertTrue(output.isNull("videoUri"))
    }
}
