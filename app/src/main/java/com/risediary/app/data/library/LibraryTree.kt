package com.risediary.app.data.library

import java.util.Locale

data class LibrarySnapshot(val assets: List<LibraryAsset> = emptyList(), val folders: List<LibraryFolder> = emptyList())
enum class LibrarySort(val label: String) { NEWEST("导入时间"), NAME("名称"), SIZE("大小"), DURATION("时长") }
data class LibraryEntry(val key: String, val name: String, val parentId: String?, val path: String,
    val asset: LibraryAsset? = null, val folder: LibraryFolder? = null)
data class LibrarySelection(val roots: Set<String>, val folderIds: Set<String>, val assetIds: Set<String>)

/** Logical directory operations never touch the media bytes or their stable identities. */
class LibraryTree(val snapshot: LibrarySnapshot) {
    private val folders = snapshot.folders.associateBy { it.id }
    private val assets = snapshot.assets.filter { it.state == "ready" }.associateBy { it.id }
    private val children = snapshot.folders.groupBy { it.parentId }

    fun path(parent: String?): String {
        val names = ArrayList<String>()
        val seen = HashSet<String>()
        var cursor = parent
        while (cursor != null && seen.add(cursor)) {
            val folder = folders[cursor] ?: break
            names.add(folder.name); cursor = folder.parentId
        }
        return (listOf("素材库") + names.asReversed()).joinToString(" / ")
    }

    fun entries(parent: String?, query: String, sort: LibrarySort): List<LibraryEntry> {
        val term = query.trim()
        val result = ArrayList<LibraryEntry>()
        folders.values.filter { if (term.isEmpty()) it.parentId == parent else it.name.contains(term, true) }.forEach {
            result.add(LibraryEntry("folder:${it.id}", it.name, it.parentId, path(it.parentId), folder = it))
        }
        assets.values.filter { if (term.isEmpty()) it.folderId == parent else it.name.contains(term, true) }.forEach {
            result.add(LibraryEntry("asset:${it.id}", it.name, it.folderId, path(it.folderId), asset = it))
        }
        val byName = compareBy<LibraryEntry> { it.name.lowercase(Locale.ROOT) }.thenBy { it.key }
        val videos = when (sort) {
            LibrarySort.NAME -> byName
            LibrarySort.NEWEST -> compareByDescending<LibraryEntry> { it.asset?.createdAt ?: 0 }.thenBy { it.key }
            LibrarySort.SIZE -> compareByDescending<LibraryEntry> { it.asset?.sizeBytes ?: 0 }.then(byName)
            LibrarySort.DURATION -> compareByDescending<LibraryEntry> { it.asset?.durationMillis ?: 0 }.then(byName)
        }
        return result.filter { it.folder != null }.sortedWith(byName) + result.filter { it.asset != null }.sortedWith(videos)
    }

    fun selection(keys: Set<String>): LibrarySelection {
        require(keys.isNotEmpty()) { "请先选择素材或文件夹" }
        val selectedFolders = keys.filter { it.startsWith("folder:") }.map { it.removePrefix("folder:") }.toSet()
        require(keys.all { key -> if (key.startsWith("folder:")) folders.containsKey(key.removePrefix("folder:"))
            else key.startsWith("asset:") && assets.containsKey(key.removePrefix("asset:")) }) { "部分内容已经变化，请重新选择" }
        fun ancestorSelected(parent: String?): Boolean {
            var cursor = parent
            val visited = HashSet<String>()
            while (cursor != null && visited.add(cursor)) {
                if (cursor in selectedFolders) return true
                cursor = folders[cursor]?.parentId
            }
            return false
        }
        val roots = keys.filter { key -> !ancestorSelected(if (key.startsWith("folder:"))
            folders.getValue(key.removePrefix("folder:")).parentId else assets.getValue(key.removePrefix("asset:")).folderId) }.toSet()
        val allFolders = HashSet<String>()
        val queue = ArrayDeque(selectedFolders)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (allFolders.add(id)) children[id].orEmpty().forEach { queue.add(it.id) }
        }
        val allAssets = assets.values.filter { "asset:${it.id}" in keys || it.folderId in allFolders }.map { it.id }.toSet()
        return LibrarySelection(roots, allFolders, allAssets)
    }

    private fun names(parent: String?, excluding: String? = null): Set<String> =
        folders.values.filter { it.parentId == parent && "folder:${it.id}" != excluding }.map { it.name }.toSet() +
            assets.values.filter { it.folderId == parent && "asset:${it.id}" != excluding }.map { it.name }
    private fun validName(value: String): String = value.trim().also {
        require(it.isNotEmpty() && it.length <= 1000 && it.none { c -> c.isISOControl() }) { "名称不能为空、包含控制字符或超过1000字" }
    }
    private fun requireParent(id: String?) { require(id == null || id in folders) { "目标文件夹已不存在，请重新选择" } }

    fun createFolder(parent: String?, name: String, id: String, createdAt: Long): LibrarySnapshot {
        requireParent(parent)
        val clean = validName(name)
        require(clean !in names(parent)) { "此位置已有同名内容，请调整名称" }
        require(id !in folders && id !in assets) { "文件夹编号已存在" }
        return snapshot.copy(folders = snapshot.folders + LibraryFolder(id, parent, clean, createdAt))
    }
    fun rename(key: String, value: String): LibrarySnapshot {
        selection(setOf(key))
        val name = validName(value)
        val parent = if (key.startsWith("folder:")) folders.getValue(key.removePrefix("folder:")).parentId
            else assets.getValue(key.removePrefix("asset:")).folderId
        require(name !in names(parent, key)) { "此位置已有同名内容，请调整名称" }
        return snapshot.copy(assets = snapshot.assets.map { if ("asset:${it.id}" == key) it.copy(name = name) else it },
            folders = snapshot.folders.map { if ("folder:${it.id}" == key) it.copy(name = name) else it })
    }
    fun move(keys: Set<String>, target: String?): LibrarySnapshot {
        requireParent(target)
        val selected = selection(keys)
        require(target !in selected.folderIds) { "不能移动到自身或下级文件夹" }
        val reserved = names(target).toMutableSet()
        val folderChanges = HashMap<String, LibraryFolder>()
        val assetChanges = HashMap<String, LibraryAsset>()
        for (key in selected.roots.sorted()) {
            if (key.startsWith("folder:")) {
                val f = folders.getValue(key.removePrefix("folder:"))
                if (f.parentId == target) continue
                val name = uniqueLibraryName(f.name, reserved); reserved.add(name)
                folderChanges[f.id] = f.copy(parentId = target, name = name)
            } else {
                val a = assets.getValue(key.removePrefix("asset:"))
                if (a.folderId == target) continue
                val name = uniqueLibraryName(a.name, reserved); reserved.add(name)
                assetChanges[a.id] = a.copy(folderId = target, name = name)
            }
        }
        return snapshot.copy(assets = snapshot.assets.map { assetChanges[it.id] ?: it }, folders = snapshot.folders.map { folderChanges[it.id] ?: it })
    }
}
