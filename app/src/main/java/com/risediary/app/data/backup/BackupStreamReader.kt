package com.risediary.app.data.backup

import android.util.JsonReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import android.util.JsonToken
import org.json.JSONObject
import java.io.InputStream
import java.io.FilterInputStream
import java.io.File
import java.math.BigDecimal
import java.util.zip.ZipInputStream

/** Counts decompressed bytes, rejects ambiguous keys, parses only one object at a time. */
internal object BackupStreamReader {
    suspend fun read(input: InputStream, stage: BackupStagingStore, directory: File,
        consume: suspend (String, JSONObject) -> StagedRow): SettingsSnapshot {
        val owner = currentCoroutineContext()[Job]
        val budget = BackupSizeBudget()
        var settings = defaultBackupSettings()
        val seen = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in BackupFiles.all && seen.add(entry.name)) { "备份含未知或重复文件" }
                budget.beginEntry()
                val limited = object : FilterInputStream(zip) {
                    private fun account(count: Int) {
                        owner?.ensureActive()
                        if (count <= 0) return
                        budget.account(count)
                        requireBackupSpace(directory.usableSpace)
                    }
                    override fun read(): Int = `in`.read().also { account(if (it < 0) 0 else 1) }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes,offset,length).also(::account)
                    override fun close() = Unit // The ZIP belongs to the outer loop.
                }
                val reader = JsonReader(java.io.InputStreamReader(limited, Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)))
                reader.isLenient = false
                if (entry.name == BackupFiles.SETTINGS) settings = BackupJsonCodec.parseSettings(readObject(reader).toString())
                else {
                    reader.beginArray()
                    while (reader.hasNext()) stage.put(consume(entry.name, readObject(reader)))
                    reader.endArray()
                }
                require(reader.peek() == JsonToken.END_DOCUMENT) { "备份含额外JSON内容" }
                zip.closeEntry()
            }
        }
        require(seen.containsAll(BackupFiles.arrays)) { "缺少必要记录文件" }
        return settings
    }
    private fun readObject(reader: JsonReader): JSONObject {
        val result = JSONObject(); val keys = hashSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(keys.add(key)) { "JSON字段重复：$key" }
            val value: Any = when (reader.peek()) {
                JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
                JsonToken.STRING -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.NUMBER -> {
                    val number = BigDecimal(reader.nextString())
                    try { number.longValueExact() } catch (_: ArithmeticException) {
                        number.toDouble().also { require(it.isFinite()) { "数值超出范围" } }
                    }
                }
                else -> error("字段 $key 格式无效")
            }
            result.put(key,value)
        }
        reader.endObject()
        return result
    }
}
