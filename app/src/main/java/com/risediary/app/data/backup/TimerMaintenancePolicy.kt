package com.risediary.app.data.backup

import com.risediary.app.service.TimerSession

/** A failed durable transition may still be pending while both visible sessions look idle. */
internal fun requireNoActiveTimerForMaintenance(
    persisted: TimerSession,
    live: TimerSession,
    persistenceFailed: Boolean
) {
    check(!persistenceFailed) { "计时尚未保存，请先返回计时页面重试，再恢复或清除数据" }
    check(!persisted.isActive && !live.isActive) { "请先处理当前计时，再恢复或清除数据" }
}
