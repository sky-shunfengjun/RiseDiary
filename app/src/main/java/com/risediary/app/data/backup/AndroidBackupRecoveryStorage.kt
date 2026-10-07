package com.risediary.app.data.backup

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

internal fun newBackupRecoveryJournal(context: Context): BackupRecoveryJournal =
    BackupRecoveryJournal(AndroidBackupRecoveryStorage(File(context.filesDir, "maintenance/backup-recovery.bin")))

/** File.exists() can hide IO/permission errors. Only confirmed absence may reopen writes. */
internal fun hasCommittedRecoveryFile(file: File): Boolean {
    var directory = file.parentFile
    while (directory != null) {
        val attributes = try {
            Files.readAttributes(directory.toPath(), BasicFileAttributes::class.java)
        } catch (_: NoSuchFileException) {
            directory = directory.parentFile
            continue
        }
        if (!attributes.isDirectory || !Files.isReadable(directory.toPath()) || !Files.isExecutable(directory.toPath())) {
            throw IOException("无法检查本地还原保护")
        }
        break
    }
    if (directory == null) throw IOException("无法检查本地还原保护")

    fun inspect(candidate: File): Boolean = try {
        Files.readAttributes(candidate.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        true
    } catch (_: NoSuchFileException) { false }

    val base = inspect(file)
    val backup = inspect(File(file.path + ".bak"))
    // A first write interrupted before AtomicFile commits leaves only .new. The database cannot
    // have changed: persist verifies the base before replacement. Leave that staging file alone;
    // a later AtomicFile.startWrite may safely replace it. Inspection failures still propagate.
    inspect(File(file.path + ".new"))
    return base || backup
}

/** App-private, excluded from system backup by the existing file-domain exclusion. */
internal class AndroidBackupRecoveryStorage(private val file: File) : BackupRecoveryStorage {
    private val atomic = AtomicFile(file)
    override fun exists(): Boolean = hasCommittedRecoveryFile(file)

    override fun openRead(): java.io.InputStream = atomic.openRead()

    override fun writeStream(block: (java.io.OutputStream) -> Unit) {
        val stream = atomic.startWrite()
        try { block(stream); stream.flush(); stream.fd.sync(); atomic.finishWrite(stream) }
        catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
    }

    override fun read(): ByteArray = atomic.openRead().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > BackupRecoveryJournal.MAX_FILE_BYTES - output.size()) throw IOException("本地还原副本过大")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    override fun write(bytes: ByteArray) {
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            stream.flush()
            // Do not rely on AtomicFile's logging-only fsync failure handling.
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    override fun delete() {
        atomic.delete()
        if (exists()) throw IOException("本地还原保护尚未移除")
    }
}
