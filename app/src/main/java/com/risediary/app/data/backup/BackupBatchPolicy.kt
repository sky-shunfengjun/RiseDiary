package com.risediary.app.data.backup

internal fun backupBatchSize(weights: List<Long>): Int {
    var bytes = 0L; var count = 0
    for (size in weights.take(500)) {
        require(size >= 0) { "记录大小无效" }
        if (count > 0 && size > 1024L * 1024 - bytes) break
        bytes = Math.addExact(bytes,size); count++
        if (bytes >= 1024L * 1024) break
    }
    return count
}
