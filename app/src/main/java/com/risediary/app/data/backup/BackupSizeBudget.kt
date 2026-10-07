package com.risediary.app.data.backup

/** Both ZIP directions count uncompressed bytes before accepting a chunk. */
internal class BackupSizeBudget {
    private var entry = 0L
    private var total = 0L
    fun beginEntry() { entry=0 }
    fun account(bytes: Int) {
        require(bytes>=0) { "备份大小无效" }
        entry += bytes; total += bytes
        require(entry<=BackupFiles.ENTRY_BYTES && total<=BackupFiles.TOTAL_BYTES) {
            "备份或恢复结果超过32 MiB单文件／128 MiB总容量"
        }
    }
}
internal fun requireBackupSpace(available: Long, needed: Long = 0) {
    require(needed>=0 && available>=Math.addExact(needed,BackupFiles.SPACE_RESERVE)) {
        "手机可用空间不足，请清理空间后重试（至少预留16 MiB）"
    }
}
