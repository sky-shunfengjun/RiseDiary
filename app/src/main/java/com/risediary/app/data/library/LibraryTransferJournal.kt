package com.risediary.app.data.library

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LibraryImportItem(val id: String, val uri: String, val name: String,
    val folderId: String? = null, val status: String = "pending", val detail: String = "")

@Serializable
data class LibraryTransferState(val items: List<LibraryImportItem> = emptyList(), val running: Boolean = false,
    val bytes: Long = 0, val totalBytes: Long? = null, val message: String = "") {
    val completed get() = items.count { it.status == "done" || it.status == "duplicate" }
    val remaining get() = items.filter { it.status != "done" && it.status != "duplicate" }
}

/** Durable queue; a process restart never silently resumes a foreground service. */
class LibraryTransferJournal(private val file: File) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun read(): LibraryTransferState {
        if (!file.exists()) return LibraryTransferState()
        val saved = json.decodeFromString<LibraryTransferState>(file.readText())
        return if (saved.running) saved.copy(running = false, bytes = 0, totalBytes = null,
            message = "上次导入已中断，已完成的素材保留，可重试剩余项目") else saved
    }
    fun write(state: LibraryTransferState) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { it.write(json.encodeToString(state).toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
