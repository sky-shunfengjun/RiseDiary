package com.risediary.app.data.backup

import androidx.datastore.preferences.core.*
import com.risediary.app.data.draft.RecordDraftEntity
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.Tag
import com.risediary.app.service.TimerSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Internal rollback state; it is never included in the user ZIP. */
internal data class BackupRecoverySnapshot(
    val flights: List<Flight>,
    val lengthRecords: List<LengthRecord>,
    val tags: List<Tag>,
    val achievements: List<Achievement>,
    val preferences: Preferences,
    val drafts: List<RecordDraftEntity>,
    val timer: TimerSession
)

/** File durability boundary, separate from parsing so the same journal is testable on the JVM. */
internal interface BackupRecoveryStorage {
    fun exists(): Boolean
    fun read(): ByteArray
    fun write(bytes: ByteArray)
    fun delete()
}

internal class BackupRecoveryJournal(private val storage: BackupRecoveryStorage) {
    /** An unreadable directory is not evidence that the original data is safe to overwrite. */
    fun pending(): Boolean = try { storage.exists() } catch (_: Exception) { true }

    fun persist(snapshot: BackupRecoverySnapshot) {
        check(!pending()) { "原数据尚待还原，请先重试还原" }
        val encoded = encode(snapshot)
        storage.write(encoded)
        // AtomicFile.finishWrite does not expose every rename failure. Verify the committed bytes.
        if (!storage.exists() || !MessageDigest.isEqual(encoded, storage.read())) {
            throw IOException("本地还原副本未完整保存")
        }
    }

    fun load(): BackupRecoverySnapshot? {
        if (!pending()) return null
        return try { decode(storage.read()) }
        catch (failure: Exception) { throw IOException("本地还原副本无法读取", failure) }
    }

    fun clear() {
        storage.delete()
        if (storage.exists()) throw IOException("本地还原保护尚未移除")
    }

    private fun encode(snapshot: BackupRecoverySnapshot): ByteArray {
        val output = LimitedOutput()
        DataOutputStream(output).use { data ->
            data.writeList(snapshot.flights) { writeFlight(it) }
            data.writeList(snapshot.lengthRecords) {
                writeLong(it.id); writeLong(it.recordDate); writeFloat(it.flaccidLengthCm)
                writeFloat(it.erectLengthCm); writeText(it.note)
            }
            data.writeList(snapshot.tags) { writeLong(it.id); writeText(it.name); writeText(it.color); writeInt(it.sortOrder) }
            data.writeList(snapshot.achievements) { writeLong(it.id); writeText(it.achievementKey); writeLong(it.unlockedAt); writeBoolean(it.notified) }
            data.writePreferences(snapshot.preferences)
            data.writeList(snapshot.drafts) {
                writeText(it.draftId); writeOptionalInt(it.activeSlot); writeLong(it.revision)
                writeText(it.payload); writeOptionalLong(it.completedFlightId)
            }
            data.writeText(json.encodeToString(snapshot.timer))
        }
        val payload = output.toByteArray()
        return ByteArrayOutputStream(payload.size + HEADER_BYTES).also { bytes ->
            DataOutputStream(bytes).use { envelope ->
                envelope.writeInt(MAGIC)
                envelope.writeInt(VERSION)
                envelope.writeInt(payload.size)
                envelope.write(MessageDigest.getInstance("SHA-256").digest(payload))
                envelope.write(payload)
            }
        }.toByteArray()
    }

    private fun decode(bytes: ByteArray): BackupRecoverySnapshot {
        require(bytes.size in HEADER_BYTES..MAX_FILE_BYTES) { "还原副本大小无效" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { envelope ->
            require(envelope.readInt() == MAGIC && envelope.readInt() == VERSION) { "还原副本版本无效" }
            val length = envelope.readInt()
            require(length >= 0 && length == bytes.size - HEADER_BYTES) { "还原副本内容不完整" }
            val expected = ByteArray(32).also(envelope::readFully)
            val payload = ByteArray(length).also(envelope::readFully)
            require(MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(payload))) { "还原副本校验失败" }
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val flights = input.readList { readFlight() }
                val lengths = input.readList { LengthRecord(readLong(), readLong(), readFloat(), readFloat(), readText()) }
                val tags = input.readList { Tag(readLong(), readText(), readText(), readInt()) }
                val achievements = input.readList { Achievement(readLong(), readText(), readLong(), readBoolean()) }
                val preferences = input.readPreferences()
                val drafts = input.readList { RecordDraftEntity(readText(), readOptionalInt(), readLong(), readText(), readOptionalLong()) }
                val timer = json.decodeFromString<TimerSession>(input.readText())
                require(input.available() == 0) { "还原副本含额外内容" }
                // Do not validate through user-ZIP rules: imperfect old rows must be recoverable exactly.
                BackupRecoverySnapshot(flights, lengths, tags, achievements, preferences, drafts, timer)
            }
        }
    }

    private fun DataOutputStream.writeFlight(value: Flight) {
        writeLong(value.id); writeLong(value.startTime); writeLong(value.endTime); writeInt(value.durationSeconds)
        writeOptionalInt(value.spurtCount); writeOptionalFloat(value.semenVolumeMl); writeText(value.volumeInputMode)
        writeOptionalFloat(value.ejaculationDistanceCm); writeText(value.methodTags); writeText(value.moodNote)
        writeLong(value.createdAt); writeLong(value.updatedAt)
        writeOptionalInt(value.legacySpurtCount); writeOptionalFloat(value.legacyVolumeMl)
        writeOptionalText(value.legacyVolumeInputMode); writeOptionalInt(value.predictionMaxTicks)
        writeOptionalText(value.videoUri); writeOptionalText(value.videoDisplayName); writeOptionalText(value.videoMimeType)
        writeOptionalText(value.recordDraftId); writeText(value.timingSource); writeText(value.globalId)
        writeText(value.recordSource); writeOptionalText(value.sourceDeviceId)
    }

    private fun DataInputStream.readFlight() = Flight(
        id = readLong(), startTime = readLong(), endTime = readLong(), durationSeconds = readInt(),
        spurtCount = readOptionalInt(), semenVolumeMl = readOptionalFloat(), volumeInputMode = readText(),
        ejaculationDistanceCm = readOptionalFloat(), methodTags = readText(), moodNote = readText(),
        createdAt = readLong(), updatedAt = readLong(), legacySpurtCount = readOptionalInt(),
        legacyVolumeMl = readOptionalFloat(), legacyVolumeInputMode = readOptionalText(), predictionMaxTicks = readOptionalInt(),
        videoUri = readOptionalText(), videoDisplayName = readOptionalText(), videoMimeType = readOptionalText(),
        recordDraftId = readOptionalText(), timingSource = readText(), globalId = readText(),
        recordSource = readText(), sourceDeviceId = readOptionalText()
    )

    private fun DataOutputStream.writePreferences(preferences: Preferences) {
        val values = preferences.asMap().entries.sortedBy { it.key.name }
        writeInt(values.size)
        values.forEach { (key, value) ->
            writeText(key.name)
            when (value) {
                is Boolean -> { writeByte(1); writeBoolean(value) }
                is Float -> { writeByte(2); writeFloat(value) }
                is Double -> { writeByte(3); writeDouble(value) }
                is Int -> { writeByte(4); writeInt(value) }
                is Long -> { writeByte(5); writeLong(value) }
                is String -> { writeByte(6); writeText(value) }
                is Set<*> -> {
                    require(value.all { it is String }) { "本地设置类型无法保存" }
                    writeByte(7); writeInt(value.size)
                    value.map { it as String }.sorted().forEach { writeText(it) }
                }
                is ByteArray -> { writeByte(8); writeInt(value.size); write(value) }
                else -> throw IOException("本地设置类型无法保存")
            }
        }
    }

    private fun DataInputStream.readPreferences(): Preferences {
        val preferences = mutablePreferencesOf()
        val names = HashSet<String>()
        repeat(readCount()) {
            val name = readText()
            require(names.add(name)) { "本地设置键重复" }
            when (readUnsignedByte()) {
                1 -> preferences[booleanPreferencesKey(name)] = readBoolean()
                2 -> preferences[floatPreferencesKey(name)] = readFloat()
                3 -> preferences[doublePreferencesKey(name)] = readDouble()
                4 -> preferences[intPreferencesKey(name)] = readInt()
                5 -> preferences[longPreferencesKey(name)] = readLong()
                6 -> preferences[stringPreferencesKey(name)] = readText()
                7 -> preferences[stringSetPreferencesKey(name)] = readList { readText() }.toSet()
                8 -> {
                    val length = readInt()
                    require(length >= 0 && length <= available()) { "本地设置内容不完整" }
                    preferences[byteArrayPreferencesKey(name)] = ByteArray(length).also(::readFully)
                }
                else -> throw IOException("本地设置类型无效")
            }
        }
        return preferences.toPreferences()
    }

    // UTF-16 preserves every stored code unit, including historical malformed surrogate input.
    private fun DataOutputStream.writeText(value: String) { writeInt(value.length); value.forEach { writeChar(it.code) } }
    private fun DataInputStream.readText(): String {
        val length = readInt()
        require(length >= 0 && length <= available() / 2) { "还原文字内容不完整" }
        return CharArray(length) { readChar() }.concatToString()
    }
    private fun DataOutputStream.writeOptionalText(value: String?) { writeBoolean(value != null); if (value != null) writeText(value) }
    private fun DataInputStream.readOptionalText(): String? = if (readBoolean()) readText() else null
    private fun DataOutputStream.writeOptionalInt(value: Int?) { writeBoolean(value != null); if (value != null) writeInt(value) }
    private fun DataInputStream.readOptionalInt(): Int? = if (readBoolean()) readInt() else null
    private fun DataOutputStream.writeOptionalLong(value: Long?) { writeBoolean(value != null); if (value != null) writeLong(value) }
    private fun DataInputStream.readOptionalLong(): Long? = if (readBoolean()) readLong() else null
    private fun DataOutputStream.writeOptionalFloat(value: Float?) { writeBoolean(value != null); if (value != null) writeFloat(value) }
    private fun DataInputStream.readOptionalFloat(): Float? = if (readBoolean()) readFloat() else null
    private fun <T> DataOutputStream.writeList(values: List<T>, writeItem: DataOutputStream.(T) -> Unit) {
        writeInt(values.size); values.forEach { writeItem(it) }
    }
    private fun DataInputStream.readCount(): Int = readInt().also {
        require(it >= 0 && it <= available()) { "还原列表内容不完整" }
    }
    private fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> = List(readCount()) { readItem() }

    private class LimitedOutput : ByteArrayOutputStream() {
        override fun write(value: Int) {
            if (count >= MAX_PAYLOAD_BYTES) throw IOException("本地还原副本过大，未替换数据")
            super.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length > MAX_PAYLOAD_BYTES - count) throw IOException("本地还原副本过大，未替换数据")
            super.write(bytes, offset, length)
        }
    }

    companion object {
        private const val MAGIC = 0x52444331
        private const val VERSION = 1
        private const val HEADER_BYTES = 44
        private const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024
        const val MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + HEADER_BYTES
        private val json = Json { encodeDefaults = true }
    }
}
