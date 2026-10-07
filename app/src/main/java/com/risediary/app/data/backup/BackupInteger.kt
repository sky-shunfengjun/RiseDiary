package com.risediary.app.data.backup

/** Reject before narrowing: JSON Long values must never wrap into plausible Int fields. */
internal fun requireBackupInt(value: Number): Int {
    require(value is Int || value is Long) { "整数格式无效" }
    val number=value.toLong()
    require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "整数超出范围" }
    return number.toInt()
}
