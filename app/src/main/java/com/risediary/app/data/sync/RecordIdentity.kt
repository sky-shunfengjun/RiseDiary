package com.risediary.app.data.sync

import com.risediary.app.data.entity.Flight
import java.util.UUID

/** Record identity only; transport, merging and remote deletion are not implemented here. */
object RecordIdentity {
    const val PHONE = "phone"
    const val WEARABLE = "wearable"
    private val canonicalUuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

    fun isValidId(value: String): Boolean = canonicalUuid.matches(value) && value != NIL_UUID

    fun newId(): String = UUID.randomUUID().toString()

    fun requireValidRecords(records: List<Flight>) {
        val identities = HashSet<String>(records.size)
        for (record in records) {
            require(canonicalUuid.matches(record.globalId) && record.globalId != NIL_UUID) {
                "记录固定编号无效"
            }
            require(identities.add(record.globalId)) { "记录固定编号重复" }
            require(record.recordSource == PHONE || record.recordSource == WEARABLE) { "记录来源无效" }
            require(record.sourceDeviceId == null ||
                (record.sourceDeviceId.isNotBlank() && record.sourceDeviceId.length <= 128)) {
                "来源设备编号无效"
            }
        }
    }
}
