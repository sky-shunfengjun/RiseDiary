package com.risediary.app.data.library

import javax.inject.Inject
import javax.inject.Singleton

/** Owners may be players, timers or unsaved forms. Reservation closes the check/delete race. */
@Singleton
class LibraryUsageRegistry @Inject constructor() {
    private val guard = Any()
    private val owners = HashMap<Any, String>()
    private val deleting = HashSet<String>()
    fun hold(owner: Any, id: String): Boolean = synchronized(guard) {
        if (id in deleting) false else { owners[owner] = id; true }
    }
    fun release(owner: Any) { synchronized(guard) { owners.remove(owner) } }
    fun reserveDeletion(ids: Set<String>): AutoCloseable = synchronized(guard) {
        check(owners.values.none { it in ids } && deleting.none { it in ids }) { "有素材正在使用，请先关闭观看、计时或未保存的记录" }
        deleting.addAll(ids)
        AutoCloseable { synchronized(guard) { deleting.removeAll(ids) } }
    }
}
