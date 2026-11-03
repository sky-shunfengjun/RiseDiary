package com.risediary.app.ui.library

/** Navigation history within one secondary folder page; the library tab stays at its root. */
internal class LibraryFolderHistory(initial: List<String>) {
    private val trail = initial.distinct().toMutableList().also { require(it.isNotEmpty()) }
    val pages: List<String> get() = trail.toList()
    val current: String get() = trail.last()
    val depth: Int get() = trail.size
    fun open(id: String) {
        val previous = trail.indexOf(id)
        if (previous >= 0) while (trail.lastIndex > previous) trail.removeAt(trail.lastIndex)
        else trail.add(id)
    }
    fun back(): Boolean {
        if (trail.size == 1) return false
        trail.removeAt(trail.lastIndex)
        return true
    }
    fun reconcile(available: Set<String>): Boolean {
        if (trail.first() !in available) return false
        val missing = trail.indexOfFirst { it !in available }
        if (missing >= 0) while (trail.size > missing) trail.removeAt(trail.lastIndex)
        return true
    }
}
