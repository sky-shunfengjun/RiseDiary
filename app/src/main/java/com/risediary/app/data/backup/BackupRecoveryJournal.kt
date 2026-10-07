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
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterInputStream
import java.security.DigestOutputStream
import kotlinx.coroutines.runBlocking
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
    fun openRead(): InputStream = ByteArrayInputStream(read())
    fun writeStream(block: (OutputStream) -> Unit) {
        val output = ByteArrayOutputStream()
        block(output)
        write(output.toByteArray())
    }
}

internal interface RecoverySource {
    val preferences: Preferences
    val timer: TimerSession
    fun flights(emit: (Flight) -> Unit)
    fun lengths(emit: (LengthRecord) -> Unit)
    fun tags(emit: (Tag) -> Unit)
    fun achievements(emit: (Achievement) -> Unit)
    fun drafts(emit: (RecordDraftEntity) -> Unit)
}
internal interface RecoverySink {
    suspend fun flight(row: Flight)
    suspend fun length(row: LengthRecord)
    suspend fun tag(row: Tag)
    suspend fun achievement(row: Achievement)
    suspend fun draft(row: RecordDraftEntity)
    suspend fun settings(preferences: Preferences, timer: TimerSession)
}


internal class BackupRecoveryJournal(private val storage: BackupRecoveryStorage) {
    /** An unreadable directory is not evidence that the original data is safe to overwrite. */
    fun pending(): Boolean = try { storage.exists() } catch (_: Exception) { true }

    fun persist(snapshot: BackupRecoverySnapshot) = persist(object : RecoverySource {
        override val preferences = snapshot.preferences
        override val timer = snapshot.timer
        override fun flights(emit: (Flight) -> Unit) = snapshot.flights.forEach(emit)
        override fun lengths(emit: (LengthRecord) -> Unit) = snapshot.lengthRecords.forEach(emit)
        override fun tags(emit: (Tag) -> Unit) = snapshot.tags.forEach(emit)
        override fun achievements(emit: (Achievement) -> Unit) = snapshot.achievements.forEach(emit)
        override fun drafts(emit: (RecordDraftEntity) -> Unit) = snapshot.drafts.forEach(emit)
    })

    fun fingerprint(source: RecoverySource): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        writePayload(DataOutputStream(CountingOutput(DigestOutputStream(DiscardOutput, digest)).buffered()), source)
        return digest.digest()
    }
    fun estimatedBytes(source: RecoverySource): Long {
        val counter = CountingOutput(DiscardOutput)
        writePayload(DataOutputStream(counter.buffered()), source)
        return counter.size.toLong() + HEADER_BYTES
    }

    fun persist(source: RecoverySource) {
        check(!pending()) { "原数据尚待还原，请先重试还原" }
        // Two bounded passes: measure/hash, then atomically write exactly that payload.
        val digest = MessageDigest.getInstance("SHA-256")
        val counter = CountingOutput(DigestOutputStream(DiscardOutput, digest))
        writePayload(DataOutputStream(counter.buffered()), source)
        val expected = digest.digest()
        storage.writeStream { stream ->
            val out = DataOutputStream(stream.buffered())
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(counter.size)
            out.write(expected)
            writePayload(out, source)
            out.flush()
        }
        verify(expected,counter.size)
    }

    fun load(): BackupRecoverySnapshot? {
        if (!pending()) return null
        val flights = mutableListOf<Flight>(); val lengths = mutableListOf<LengthRecord>()
        val tags = mutableListOf<Tag>(); val achievements = mutableListOf<Achievement>()
        val drafts = mutableListOf<RecordDraftEntity>()
        var restoredPreferences: Preferences = emptyPreferences(); var restoredTimer = TimerSession()
        runBlocking { replay(object : RecoverySink {
            override suspend fun flight(row: Flight) { flights.add(row) }
            override suspend fun length(row: LengthRecord) { lengths.add(row) }
            override suspend fun tag(row: Tag) { tags.add(row) }
            override suspend fun achievement(row: Achievement) { achievements.add(row) }
            override suspend fun draft(row: RecordDraftEntity) { drafts.add(row) }
            override suspend fun settings(preferences: Preferences, timer: TimerSession) { restoredPreferences = preferences; restoredTimer = timer }
        }) }
        return BackupRecoverySnapshot(flights, lengths, tags, achievements, restoredPreferences, drafts, restoredTimer)
    }

    private fun verify(expectedHash: ByteArray? = null, expectedLength: Int? = null) {
        try { verifyInternal(expectedHash,expectedLength) } catch (failure: Exception) { throw IOException("本地还原副本未完整保存或校验失败",failure) }
    }
    private fun verifyInternal(expectedHash: ByteArray?, expectedLength: Int?) {
        storage.openRead().buffered().use { stream ->
            val input = DataInputStream(stream)
            require(input.readInt() == MAGIC) { "还原副本版本无效" }
            require(input.readInt() in 1..VERSION) { "还原副本版本无效" }
            val length = input.readInt()
            require(length in 0..MAX_PAYLOAD_BYTES) { "还原副本大小无效" }
            val expected = ByteArray(32).also(input::readFully)
            require(expectedLength == null || expectedLength == length) { "保护副本与原数据大小不一致" }
            require(expectedHash == null || MessageDigest.isEqual(expectedHash,expected)) { "保护副本与原数据不一致" }
            val digest = MessageDigest.getInstance("SHA-256")
            var remaining = length
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                require(count > 0) { "还原副本内容不完整" }
                digest.update(buffer, 0, count); remaining -= count
            }
            require(input.read() == -1 && MessageDigest.isEqual(expected, digest.digest())) { "还原副本校验失败" }
        }
    }

    suspend fun replay(sink: RecoverySink) {
        verify() // Complete checksum verification precedes every live mutation.
        storage.openRead().buffered().use { stream ->
            val envelope = DataInputStream(stream)
            envelope.readInt(); val version = envelope.readInt(); val size = envelope.readInt()
            envelope.skipBytes(32)
            val input = DataInputStream(RemainingInput(envelope, size))
            suspend fun rows(consume: suspend () -> Unit) { repeat(input.readCount()) { consume() } }
            rows { sink.flight(input.readFlight()) }
            rows {
                val id = input.readLong(); val date = input.readLong()
                val flaccid = input.readFloat(); val erect = input.readFloat(); val note = input.readText()
                val global = if (version >= 2) input.readText() else java.util.UUID.nameUUIDFromBytes(
                    "legacy-recovery:$id:$date".toByteArray()).toString()
                sink.length(LengthRecord(id, date, flaccid, erect, note, global))
            }
            rows { sink.tag(Tag(input.readLong(), input.readText(), input.readText(), input.readInt())) }
            rows { sink.achievement(Achievement(input.readLong(), input.readText(), input.readLong(), input.readBoolean())) }
            val preferences = input.readPreferences()
            rows { sink.draft(RecordDraftEntity(input.readText(), input.readOptionalInt(), input.readLong(), input.readText(), input.readOptionalLong())) }
            val timer = json.decodeFromString<TimerSession>(input.readText())
            require(input.available() == 0) { "还原副本含额外内容" }
            sink.settings(preferences, timer)
        }
    }

    fun clear() {
        storage.delete()
        if (storage.exists()) throw IOException("本地还原保护尚未移除")
    }

    private fun writePayload(data: DataOutputStream, source: RecoverySource) {
        // Counts are bounded scans, never a materialized record list.
        fun <T> rows(read: ((T) -> Unit) -> Unit, write: (T) -> Unit) {
            var count = 0; read { count = Math.addExact(count, 1) }
            data.writeInt(count); var emitted = 0
            read { write(it); emitted++ }
            check(count == emitted) { "保护副本写入期间数据发生变化" }
        }
        rows(source::flights) { data.writeFlight(it) }
        rows(source::lengths) {
            data.writeLong(it.id); data.writeLong(it.recordDate); data.writeFloat(it.flaccidLengthCm)
            data.writeFloat(it.erectLengthCm); data.writeText(it.note); data.writeText(it.globalId)
        }
        rows(source::tags) { data.writeLong(it.id); data.writeText(it.name); data.writeText(it.color); data.writeInt(it.sortOrder) }
        rows(source::achievements) { data.writeLong(it.id); data.writeText(it.achievementKey); data.writeLong(it.unlockedAt); data.writeBoolean(it.notified) }
        data.writePreferences(source.preferences)
        rows(source::drafts) {
            data.writeText(it.draftId); data.writeOptionalInt(it.activeSlot); data.writeLong(it.revision)
            data.writeText(it.payload); data.writeOptionalLong(it.completedFlightId)
        }
        data.writeText(json.encodeToString(source.timer))
        data.flush()
    }

    private class CountingOutput(private val output: OutputStream) : OutputStream() {
        var size = 0; private set
        override fun write(value: Int) { require(size < MAX_PAYLOAD_BYTES) { "本地还原副本超过512 MiB" }; output.write(value); size++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(length <= MAX_PAYLOAD_BYTES - size) { "本地还原副本超过512 MiB" }
            output.write(bytes, offset, length); size += length
        }
    }
    private class RemainingInput(input: InputStream, private var remaining: Int) : FilterInputStream(input) {
        override fun available() = remaining
        override fun read(): Int { if (remaining == 0) return -1; return super.read().also { if (it >= 0) remaining-- } }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) return -1
            return super.read(bytes, offset, minOf(length, remaining)).also { if (it > 0) remaining -= it }
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

    companion object {
        private const val MAGIC = 0x52444331
        private const val VERSION = 2
        private const val HEADER_BYTES = 44
        private const val MAX_PAYLOAD_BYTES = 512 * 1024 * 1024 - HEADER_BYTES
        const val MAX_FILE_BYTES = MAX_PAYLOAD_BYTES + HEADER_BYTES
        private val json = Json { encodeDefaults = true }
    }
}
