package com.risediary.app.data.library

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

data class LibraryCopy(val size: Long, val sha256: String)

/** Byte storage only: bounded memory, stable IDs, same-filesystem atomic publication. */
class LibraryFileStorage(val root: File) {
    private fun checked(id: String): String {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        return id
    }
    fun staging(id: String) = File(root, "staging/${checked(id)}")
    fun objectDirectory(id: String) = File(root, "objects/${checked(id)}")
    fun media(id: String) = File(objectDirectory(id), "media.bin")
    fun cover(id: String) = File(objectDirectory(id), "cover.bin")
    fun initialize() {
        if (!root.exists() && !root.mkdirs()) throw IOException("无法创建素材库目录")
        File(root, ".nomedia").also { if (!it.exists() && !it.createNewFile()) throw IOException("无法创建隐私标记") }
    }
    fun copy(id: String, input: InputStream, expectedSize: Long?,
        checkCancelled: () -> Unit = {}, progress: (Long) -> Unit = {}): LibraryCopy {
        initialize()
        val stage = staging(id)
        stage.deleteRecursively()
        if (!stage.mkdirs()) throw IOException("无法创建暂存目录")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            FileOutputStream(File(stage, "media.bin")).use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    checkCancelled()
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    output.write(buffer, 0, n); digest.update(buffer, 0, n)
                    count = Math.addExact(count, n.toLong()); progress(count)
                }
                output.fd.sync()
            }
            checkCancelled()
            if (count == 0L || (expectedSize != null && expectedSize >= 0 && count != expectedSize))
                throw IOException("视频复制不完整，请重试")
            return LibraryCopy(count, digest.digest().hex())
        } catch (error: Throwable) { stage.deleteRecursively(); throw error }
    }
    fun verify(id: String, copy: LibraryCopy, checkCancelled: () -> Unit = {}) {
        val file = File(staging(id), "media.bin")
        if (file.length() != copy.size) throw IOException("视频大小校验失败")
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) { checkCancelled(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        if (digest.digest().hex() != copy.sha256) throw IOException("视频内容校验失败")
    }
    fun commit(id: String, copy: LibraryCopy, checkCancelled: () -> Unit = {}) {
        verify(id, copy, checkCancelled)
        val target = objectDirectory(id)
        if (!target.parentFile!!.exists() && !target.parentFile!!.mkdirs()) throw IOException("无法创建素材目录")
        if (target.exists() || !staging(id).renameTo(target)) throw IOException("无法保存素材")
    }
    fun discardStaging(id: String) { staging(id).deleteRecursively() }
    fun discardObject(id: String) { objectDirectory(id).deleteRecursively() }
    fun removeObject(id: String) {
        val directory = objectDirectory(id)
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("部分素材文件未能清理，请重试")
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}

fun uniqueLibraryName(requested: String, occupied: Set<String>): String {
    val name = requested.trim().ifEmpty { "视频" }.take(1000)
    if (name !in occupied) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val stem = name.substring(0, dot); val suffix = name.substring(dot)
    var number = 2L
    while ("$stem ($number)$suffix" in occupied) number++
    return "$stem ($number)$suffix"
}
