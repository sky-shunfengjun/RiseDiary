package com.risediary.app.data.backup

import com.risediary.app.media.VideoFileAccess
import com.risediary.app.media.AndroidVideoFileAccess
import com.risediary.app.media.countUnavailableVideos
import kotlinx.coroutines.CancellationException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import androidx.datastore.preferences.core.Preferences
import com.risediary.app.data.HomeCardOrderPolicy
import com.risediary.app.util.RecordValidation
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.SeedData
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.UsernamePolicy
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.entity.Tag
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

sealed interface BackupResult {
    data class Success(val message: String, val uri: Uri? = null) : BackupResult
    data class Failure(val message: String, val cause: Throwable? = null) : BackupResult
}

data class BackupData(
    val flights: List<Flight>,
    val lengthRecords: List<LengthRecord>,
    val tags: List<Tag>,
    val achievements: List<Achievement>,
    val settings: SettingsSnapshot
)

data class SettingsSnapshot(
    val username: String,
    val mlPerSpurt: Float,
    val defaultVolumeMode: DefaultVolumeMode,
    val dailyReminderEnabled: Boolean,
    val dailyReminderTime: String,
    val inactiveReminderEnabled: Boolean,
    val inactiveReminderDays: Int,
    val inactiveReminderTime: String,
    val monthlyLengthReminderEnabled: Boolean,
    val monthlyLengthReminderDay: Int,
    val monthlyLengthReminderTime: String,
    val reminderSound: Boolean,
    val reminderVibration: Boolean,
    val backgroundAutoLockEnabled: Boolean,
    val backgroundLockMode: BackgroundLockMode,
    val themeMode: String,
    val homeCardOrder: String,
    val homeCardVisibility: String,
    val onboardingCompleted: Boolean,
    val predictionMaxTicks: Int = 80
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val preferences: UserPreferences,
    private val clock: Clock,
    private val timerStore: com.risediary.app.service.TimerSessionStore,
    private val timerHolder: com.risediary.app.service.TimerStateHolder,
    private val videoAccess: VideoFileAccess = AndroidVideoFileAccess(context)
) {
    val needsUserSelectedExportDestination: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun defaultFilename(): String =
        "RiseDiary_backup_${LocalDate.now(clock.withZone(java.time.ZoneId.systemDefault()))}.zip"

    suspend fun exportToDownloads(): BackupResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext BackupResult.Failure("请选择备份文件的保存位置")
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, defaultFilename())
            put(MediaStore.Downloads.MIME_TYPE, MIME_ZIP)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext BackupResult.Failure("无法在下载目录创建文件")
        try {
            val output = resolver.openOutputStream(uri, "w")
                ?: error("无法打开备份文件")
            output.use { writeBackup(it, snapshot()) }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            BackupResult.Success("已保存到 Downloads/${defaultFilename()}", uri)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            BackupResult.Failure("导出失败：${error.readableMessage()}", error)
        }
    }

    suspend fun exportToUri(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: error("无法打开所选文件")
            output.use { writeBackup(it, snapshot()) }
            BackupResult.Success("备份已保存", uri)
        }.getOrElse { BackupResult.Failure("导出失败：${it.readableMessage()}", it) }
    }

    private data class Preimage(val data: BackupData, val preferences: Preferences,
        val drafts: List<com.risediary.app.data.draft.RecordDraftEntity>,
        val timer: com.risediary.app.service.TimerSession)
    private var recoveryPreimage: Preimage? = null
    val maintenanceState get() = preferences.maintenanceGate.state

    suspend fun restoreFromUri(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val imported = runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
            input.use(::readBackup)
        }.getOrElse { return@withContext BackupResult.Failure("备份无效：${it.readableMessage()}", it) }
        val result = replaceAll(imported, clearLock = false)
        if (result !is BackupResult.Success) return@withContext result
        // Data has already committed. An inspection failure must not report restore failure.
        val unavailable = try {
            videoAccess.countUnavailableVideos(imported.flights)
        } catch (_: CancellationException) {
            return@withContext result
        } catch (_: Exception) {
            return@withContext BackupResult.Success("数据恢复成功，视频可在记录详情中重新关联")
        }
        if (unavailable == 0) result
        else BackupResult.Success("数据恢复成功，${unavailable}条记录的视频需重新关联")
    }

    suspend fun clearAll(): BackupResult = withContext(Dispatchers.IO) {
        replaceAll(BackupData(emptyList(), emptyList(), SeedData.defaultTags, emptyList(), defaultSettings()), true)
    }

    private suspend fun replaceAll(replacement: BackupData, clearLock: Boolean): BackupResult = try {
        preferences.maintenanceGate.maintenance {
            val currentTimer = timerStore.load()
            check(!currentTimer.isActive) { "请先处理当前计时，再恢复或清除数据" }
            val raw = preferences.rawSnapshot()
            val original = Preimage(snapshotLocked(raw), raw, database.recordDraftDao().getAll(), currentTimer)
            recoveryPreimage = original
            try {
                replaceDatabase(replacement)
                preferences.applySettingsForMaintenance(replacement.settings)
                if (clearLock) preferences.clearAppLockForMaintenance()
                timerStore.save(com.risediary.app.service.TimerSession())
                timerHolder.set(com.risediary.app.service.TimerSession())
                recoveryPreimage = null
                BackupResult.Success(if (clearLock) "所有数据已清除" else "数据恢复成功")
            } catch (failure: Throwable) {
                val rollback = runCatching { restorePreimage(original) }
                if (rollback.isSuccess) {
                    recoveryPreimage = null
                    BackupResult.Failure("操作失败，已还原原数据：${failure.readableMessage()}", failure)
                } else {
                    preferences.maintenanceGate.requireRecovery()
                    BackupResult.Failure("操作失败，原数据尚未完整还原。当前暂时只读，请重试还原。", rollback.exceptionOrNull())
                }
            }
        }
    } catch (failure: Throwable) { BackupResult.Failure("操作未完成：${failure.readableMessage()}", failure) }

    suspend fun retryRecovery(): BackupResult = withContext(Dispatchers.IO) {
        try {
            preferences.maintenanceGate.maintenance(recovery = true) {
                val original = checkNotNull(recoveryPreimage) { "没有待还原的操作" }
                try {
                    restorePreimage(original)
                    recoveryPreimage = null
                    BackupResult.Success("原数据已完整还原")
                } catch (failure: Throwable) {
                    preferences.maintenanceGate.requireRecovery()
                    BackupResult.Failure("还原尚未完成，请稍后重试。", failure)
                }
            }
        } catch (failure: Throwable) { BackupResult.Failure("还原尚未完成，请稍后重试。", failure) }
    }

    private suspend fun restorePreimage(original: Preimage) {
        replaceDatabase(original.data, original.drafts)
        preferences.restoreRaw(original.preferences)
        timerStore.save(original.timer)
        timerHolder.set(original.timer)
    }

    private suspend fun snapshot(): BackupData = preferences.maintenanceGate.write {
        snapshotLocked(preferences.rawSnapshot())
    }

    private suspend fun snapshotLocked(raw: Preferences): BackupData = database.withTransaction {
        BackupData(
            database.flightDao().getAll(), database.lengthRecordDao().getAll(),
            database.tagDao().getAll(), database.achievementDao().getAll(),
            preferences.settingsSnapshot(raw)
        )
    }
    private suspend fun replaceDatabase(data: BackupData, drafts: List<com.risediary.app.data.draft.RecordDraftEntity> = emptyList()) {
        database.withTransaction {
            database.recordDraftDao().nuke()
            database.flightDao().nuke()
            database.lengthRecordDao().nuke()
            database.tagDao().nuke()
            database.achievementDao().nuke()
            data.flights.forEach { database.flightDao().insert(it) }
            data.lengthRecords.forEach { database.lengthRecordDao().insert(it) }
            data.tags.forEach { database.tagDao().insert(it) }
            data.achievements.forEach { database.achievementDao().insert(it) }
            drafts.forEach { database.recordDraftDao().insert(it) }
        }
    }

    private fun writeBackup(output: OutputStream, data: BackupData) {
        var totalBytes = 0L
        ZipOutputStream(output.buffered()).use { zip ->
            fun writeEntry(name: String, json: String) {
                val bytes = json.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_ENTRY_BYTES) { "$name 超过 5 MB" }
                totalBytes += bytes.size
                require(totalBytes <= MAX_TOTAL_BYTES) { "备份内容超过 20 MB" }
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }

            // Streams an array entry item by item so peak memory stays bounded by
            // the largest single record instead of the whole database dump.
            // Entry accounting uses the uncompressed UTF-8 bytes, matching the
            // decompressed limits enforced on import.
            fun writeArrayEntry(name: String, items: List<JSONObject>) {
                zip.putNextEntry(ZipEntry(name))
                var entryBytes = 0L

                fun writeChunk(bytes: ByteArray) {
                    entryBytes += bytes.size
                    require(entryBytes <= MAX_ENTRY_BYTES) { "$name 超过 5 MB" }
                    zip.write(bytes)
                }

                writeChunk("[".toByteArray(Charsets.UTF_8))
                items.forEachIndexed { index, item ->
                    if (index > 0) writeChunk(",".toByteArray(Charsets.UTF_8))
                    writeChunk(item.toString().toByteArray(Charsets.UTF_8))
                }
                writeChunk("]".toByteArray(Charsets.UTF_8))

                totalBytes += entryBytes
                require(totalBytes <= MAX_TOTAL_BYTES) { "备份内容超过 20 MB" }
                zip.closeEntry()
            }

            writeArrayEntry(FLIGHTS, data.flights.map(BackupJsonCodec::flightToJson))
            writeArrayEntry(LENGTHS, data.lengthRecords.map(BackupJsonCodec::lengthToJson))
            writeArrayEntry(TAGS, data.tags.map(BackupJsonCodec::tagToJson))
            writeArrayEntry(
                ACHIEVEMENTS,
                data.achievements.map(BackupJsonCodec::achievementToJson)
            )
            writeEntry(SETTINGS, BackupJsonCodec.settingsToJson(data.settings).toString())
        }
    }

    internal fun readBackup(input: InputStream): BackupData {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(
                    name in REQUIRED_ENTRIES &&
                        !name.contains('/') &&
                        !name.contains('\\') &&
                        !name.contains("..")
                ) { "包含不允许的文件：$name" }
                require(name !in entries) { "包含重复文件：$name" }

                val bytes = readEntryLimited(zip, MAX_ENTRY_BYTES)
                total += bytes.size
                require(total <= MAX_TOTAL_BYTES) { "解压后的内容超过 20 MB" }
                entries[name] = bytes
                zip.closeEntry()
            }
        }
        val missing = REQUIRED_ENTRIES - entries.keys
        require(missing.isEmpty()) { "缺少必要文件：${missing.joinToString()}" }

        return BackupData(
            flights = BackupJsonCodec.parseFlights(entries.getValue(FLIGHTS).toUtf8()),
            lengthRecords = BackupJsonCodec.parseLengths(entries.getValue(LENGTHS).toUtf8()),
            tags = BackupJsonCodec.parseTags(entries.getValue(TAGS).toUtf8()),
            achievements = BackupJsonCodec.parseAchievements(
                entries.getValue(ACHIEVEMENTS).toUtf8()
            ),
            settings = BackupJsonCodec.parseSettings(entries.getValue(SETTINGS).toUtf8()).let { settings ->
                settings.copy(homeCardOrder = HomeCardOrderPolicy.normalizeStoredOrder(settings.homeCardOrder))
            }
        ).also(::validate)
    }

    private fun validate(data: BackupData) {
        require(data.flights.map(Flight::id).distinct().size == data.flights.size) {
            "飞行记录 ID 重复"
        }
        val draftKeys = data.flights.mapNotNull { it.recordDraftId }
        require(draftKeys.distinct().size == draftKeys.size && draftKeys.all { it.isNotBlank() && it.length <= 128 }) {
            "记录提交编号无效或重复"
        }
        data.flights.forEach { flight ->
            require(flight.id >= 0L) { "飞行记录 ID 无效" }
            require(flight.startTime > 0L && flight.endTime >= flight.startTime) {
                "飞行记录时间无效"
            }
            require(flight.durationSeconds in 1..com.risediary.app.util.DurationPolicy.MAX_SECONDS) { "飞行时长超出范围" }
            require(com.risediary.app.util.RecordTimingPolicy.validate(
                flight.startTime, flight.endTime, flight.durationSeconds, flight.timingSource) == null) {
                "飞行记录时间与时长不一致"
            }
            require(com.risediary.app.media.validateLocalVideoFields(flight.videoUri, flight.videoDisplayName, flight.videoMimeType) == null) {
                "视频关联信息无效"
            }
            require(RecordValidation.validateStoredQuantity(flight.spurtCount, flight.semenVolumeMl) == null) { "射精量超出存储范围" }
            require(flight.semenVolumeMl?.let { it.isFinite() && it > 0f && it <= 100_000f } != false) {
                "射精量超出范围"
            }
            require(flight.spurtCount != null || flight.semenVolumeMl != null) {
                "飞行记录缺少射精量"
            }
            require(
                flight.volumeInputMode in RecordVolumeMode.entries.map { it.storedValue }
            ) { "飞行记录录入单位无效" }
            if (flight.legacyVolumeInputMode != null) {
                require(flight.legacyVolumeInputMode in listOf("spurts", "milliliters")) { "原数量模式无效" }
                require(RecordValidation.validateStoredQuantity(flight.legacySpurtCount, flight.legacyVolumeMl) == null) { "原数量信息无效" }
            } else {
                require(flight.legacySpurtCount == null && flight.legacyVolumeMl == null) { "原数量缺少模式" }
            }
            if (flight.volumeInputMode == RecordVolumeMode.ESTIMATED.storedValue) {
                val maximum = flight.predictionMaxTicks
                require(maximum != null && maximum in 1..10_000) { "预测记录缺少有效上限" }
                val volume = flight.semenVolumeMl
                require(flight.spurtCount == null && volume != null && volume in 0.1f..(maximum / 10f)) { "预测数量超出范围" }
                require(abs(volume * 10 - kotlin.math.round(volume * 10)) < 0.001f) { "预测数量最多保留一位小数" }
            } else {
                require(flight.predictionMaxTicks == null) { "毫升或旧股数记录不能含预测上限" }
            }
            require(flight.ejaculationDistanceCm?.let { it in 0f..1_000f } != false) {
                "射精距离超出范围"
            }
            val tags = JSONArray(flight.methodTags)
            require(tags.length() <= 100) { "标签数据无效" }
            repeat(tags.length()) { index ->
                require(tags.opt(index) is String && tags.getString(index).trim().length in 1..20) {
                    "标签数据无效"
                }
            }

            require(flight.createdAt > 0L && flight.updatedAt >= flight.createdAt) {
                "飞行记录修改时间无效"
            }
        }

        require(data.lengthRecords.map(LengthRecord::id).distinct().size == data.lengthRecords.size) {
            "长度记录 ID 重复"
        }
        data.lengthRecords.forEach {
            require(it.id >= 0L) { "长度记录 ID 无效" }
            require(it.recordDate > 0L) { "长度记录日期无效" }
            require(it.flaccidLengthCm in 0.1f..100f) { "疲软长度超出范围" }
            require(it.erectLengthCm in 0.1f..100f) { "勃起长度超出范围" }

        }

        require(data.tags.map(Tag::id).distinct().size == data.tags.size) { "标签 ID 重复" }
        require(data.tags.map { it.name.lowercase(Locale.ROOT) }.distinct().size == data.tags.size) {
            "标签名称重复"
        }
        data.tags.forEach {
            require(it.id >= 0L) { "标签 ID 无效" }
            require(it.name.trim().length in 1..20) { "标签名称无效" }
            require(it.color.matches(COLOR_PATTERN)) { "标签颜色无效" }
        }

        require(data.achievements.map(Achievement::id).distinct().size == data.achievements.size) {
            "成就 ID 重复"
        }
        require(data.achievements.map(Achievement::achievementKey).distinct().size ==
            data.achievements.size) { "成就键重复" }
        data.achievements.forEach {
            require(it.id >= 0L) { "成就 ID 无效" }
            require(
                it.achievementKey in KNOWN_ACHIEVEMENTS ||
                    it.achievementKey.startsWith("first_of_month_")
            ) { "包含未知成就" }
            require(it.unlockedAt > 0L) { "成就时间无效" }
        }

        validateSettings(data.settings)
    }

    private fun validateSettings(settings: SettingsSnapshot) {
        require(UsernamePolicy.isWithinLimit(settings.username)) { "用户名过长" }
        com.risediary.app.util.PredictionQuantitySettings.requireMaximum(settings.predictionMaxTicks)
        require(settings.mlPerSpurt in 0.1f..100f) { "每股换算值无效" }
        require(settings.dailyReminderTime.matches(Regex("""([01]\d|2[0-3]):[0-5]\d"""))) {
            "提醒时间无效"
        }
        require(settings.inactiveReminderDays in setOf(3, 7, 14, 30)) {
            "未记录提醒天数无效"
        }
        require(settings.inactiveReminderTime.matches(Regex("""([01]\d|2[0-3]):[0-5]\d"""))) {
            "未记录提醒时间无效"
        }
        require(settings.monthlyLengthReminderDay in 1..28) { "每月提醒日期无效" }
        require(
            settings.monthlyLengthReminderTime.matches(
                Regex("""([01]\d|2[0-3]):[0-5]\d""")
            )
        ) {
            "每月长度提醒时间无效"
        }
        require(settings.themeMode in setOf("system", "light", "dark")) { "主题值无效" }
        HomeCardOrderPolicy.normalizeStoredOrder(settings.homeCardOrder)
        HomeCardOrderPolicy.validateVisibility(settings.homeCardVisibility)
    }

    private fun readEntryLimited(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
        var count = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "单个文件超过 5 MB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun defaultSettings() = SettingsSnapshot(
        username = "机长",
        mlPerSpurt = 2f,
        defaultVolumeMode = DefaultVolumeMode.MILLILITERS,
        dailyReminderEnabled = false,
        dailyReminderTime = "22:00",
        inactiveReminderEnabled = false,
        inactiveReminderDays = 7,
        inactiveReminderTime = "22:00",
        monthlyLengthReminderEnabled = false,
        monthlyLengthReminderDay = 1,
        monthlyLengthReminderTime = "22:00",
        reminderSound = true,
        reminderVibration = true,
        backgroundAutoLockEnabled = false,
        backgroundLockMode = BackgroundLockMode.ALWAYS,
        themeMode = "system",
        homeCardOrder = "[]",
        homeCardVisibility = "{}",
        onboardingCompleted = false
    )

    private fun ByteArray.toUtf8(): String = toString(Charsets.UTF_8)
    private fun Throwable.readableMessage(): String =
        message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    private companion object {
        const val MIME_ZIP = "application/zip"
        const val MAX_ENTRY_BYTES = 5 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 20L * 1024L * 1024L
        const val FLIGHTS = "flights.json"
        const val LENGTHS = "length_records.json"
        const val TAGS = "tags.json"
        const val ACHIEVEMENTS = "achievements.json"
        const val SETTINGS = "settings.json"
        val REQUIRED_ENTRIES = setOf(FLIGHTS, LENGTHS, TAGS, ACHIEVEMENTS, SETTINGS)
        val COLOR_PATTERN = Regex("#[0-9A-Fa-f]{6}")
        val KNOWN_ACHIEVEMENTS = setOf(
            "milestone_1", "milestone_10", "milestone_50", "milestone_100",
            "milestone_500", "record_distance_30", "record_distance_80",
            "record_duration_30", "record_duration_60", "record_volume_high",
            "marathon", "speedster", "streak_7", "streak_30", "streak_90",
            "volume_100ml", "volume_500ml", "tag_5_types", "length_first",
            "length_growth_2cm", "special_30day_record"
        )
    }
}
