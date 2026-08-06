package com.risediary.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
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
    val onboardingCompleted: Boolean
)

private data class AppLockSnapshot(
    val enabled: Boolean,
    val credential: String,
    val attempts: Int,
    val lockoutUntil: Long
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val preferences: UserPreferences,
    private val clock: Clock
) {
    val needsUserSelectedExportDestination: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun defaultFilename(): String =
        "RiseDiary_backup_${LocalDate.now(clock)}.zip"

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

    suspend fun restoreFromUri(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val oldData = runCatching { snapshot() }.getOrElse {
            return@withContext BackupResult.Failure("读取现有数据失败：${it.readableMessage()}", it)
        }
        val imported = runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选文件")
            input.use(::readBackup)
        }.getOrElse {
            return@withContext BackupResult.Failure("备份无效：${it.readableMessage()}", it)
        }

        try {
            replaceDatabase(imported)
            try {
                applySettings(imported.settings)
            } catch (settingsError: Throwable) {
                replaceDatabase(oldData)
                runCatching { applySettings(oldData.settings) }
                throw IllegalStateException("设置恢复失败，原数据已还原", settingsError)
            }
            BackupResult.Success("数据恢复成功")
        } catch (error: Throwable) {
            BackupResult.Failure("恢复失败：${error.readableMessage()}", error)
        }
    }

    suspend fun clearAll(): BackupResult = withContext(Dispatchers.IO) {
        val oldData = runCatching { snapshot() }.getOrElse {
            return@withContext BackupResult.Failure("读取现有数据失败：${it.readableMessage()}", it)
        }
        val oldAppLock = runCatching { readAppLock() }.getOrElse {
            return@withContext BackupResult.Failure("读取应用锁设置失败：${it.readableMessage()}", it)
        }
        val cleared = BackupData(
            flights = emptyList(),
            lengthRecords = emptyList(),
            tags = SeedData.defaultTags,
            achievements = emptyList(),
            settings = defaultSettings()
        )
        try {
            replaceDatabase(cleared)
            try {
                applySettings(cleared.settings)
                preferences.setAppLock(false, "")
            } catch (settingsError: Throwable) {
                replaceDatabase(oldData)
                runCatching { applySettings(oldData.settings) }
                runCatching { restoreAppLock(oldAppLock) }
                throw IllegalStateException("设置清除失败，原数据已还原", settingsError)
            }
            BackupResult.Success("所有数据已清除")
        } catch (error: Throwable) {
            BackupResult.Failure("清除失败：${error.readableMessage()}", error)
        }
    }

    private suspend fun snapshot(): BackupData = BackupData(
        flights = database.flightDao().getAll(),
        lengthRecords = database.lengthRecordDao().getAll(),
        tags = database.tagDao().getAll(),
        achievements = database.achievementDao().getAll(),
        settings = readSettings()
    )

    private suspend fun replaceDatabase(data: BackupData) {
        database.withTransaction {
            database.flightDao().nuke()
            database.lengthRecordDao().nuke()
            database.tagDao().nuke()
            database.achievementDao().nuke()
            data.flights.forEach { database.flightDao().insert(it) }
            data.lengthRecords.forEach { database.lengthRecordDao().insert(it) }
            data.tags.forEach { database.tagDao().insert(it) }
            data.achievements.forEach { database.achievementDao().insert(it) }
        }
    }

    private suspend fun readSettings(): SettingsSnapshot = SettingsSnapshot(
        username = preferences.username.first(),
        mlPerSpurt = preferences.mlPerSpurt.first(),
        defaultVolumeMode = preferences.defaultVolumeMode.first(),
        dailyReminderEnabled = preferences.dailyReminderEnabled.first(),
        dailyReminderTime = preferences.dailyReminderTime.first(),
        inactiveReminderEnabled = preferences.inactiveReminderEnabled.first(),
        inactiveReminderDays = preferences.inactiveReminderDays.first(),
        inactiveReminderTime = preferences.inactiveReminderTime.first(),
        monthlyLengthReminderEnabled = preferences.monthlyLengthReminderEnabled.first(),
        monthlyLengthReminderDay = preferences.monthlyLengthReminderDay.first(),
        monthlyLengthReminderTime = preferences.monthlyLengthReminderTime.first(),
        reminderSound = preferences.reminderSound.first(),
        reminderVibration = preferences.reminderVibration.first(),
        backgroundAutoLockEnabled = preferences.backgroundAutoLockEnabled.first(),
        backgroundLockMode = preferences.backgroundLockMode.first(),
        themeMode = preferences.themeMode.first(),
        homeCardOrder = preferences.homeCardOrder.first(),
        homeCardVisibility = preferences.homeCardVisibility.first(),
        onboardingCompleted = preferences.onboardingCompleted.first()
    )

    private suspend fun readAppLock() = AppLockSnapshot(
        enabled = preferences.appLockEnabled.first(),
        credential = preferences.appLockPin.first(),
        attempts = preferences.appLockAttempts.first(),
        lockoutUntil = preferences.appLockoutUntil.first()
    )

    private suspend fun restoreAppLock(snapshot: AppLockSnapshot) {
        preferences.setAppLock(snapshot.enabled, snapshot.credential)
        preferences.setAppLockFailureState(snapshot.attempts, snapshot.lockoutUntil)
    }

    private suspend fun applySettings(settings: SettingsSnapshot) {
        preferences.setUsername(settings.username)
        preferences.setMlPerSpurt(settings.mlPerSpurt)
        preferences.setDefaultVolumeMode(settings.defaultVolumeMode)
        preferences.setDailyReminder(
            settings.dailyReminderEnabled,
            settings.dailyReminderTime
        )
        preferences.setInactiveReminder(
            settings.inactiveReminderEnabled,
            settings.inactiveReminderDays,
            settings.inactiveReminderTime
        )
        preferences.setMonthlyLengthReminder(
            settings.monthlyLengthReminderEnabled,
            settings.monthlyLengthReminderDay,
            settings.monthlyLengthReminderTime
        )
        preferences.setReminderSound(settings.reminderSound)
        preferences.setReminderVibration(settings.reminderVibration)
        preferences.setBackgroundAutoLockEnabled(settings.backgroundAutoLockEnabled)
        preferences.setBackgroundLockMode(settings.backgroundLockMode)
        preferences.setThemeMode(settings.themeMode)
        preferences.setHomeCardOrder(settings.homeCardOrder)
        preferences.setHomeCardVisibility(settings.homeCardVisibility)
        preferences.setOnboardingCompleted(settings.onboardingCompleted)
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

            writeEntry(FLIGHTS, BackupJsonCodec.flightsToJson(data.flights).toString(2))
            writeEntry(LENGTHS, BackupJsonCodec.lengthsToJson(data.lengthRecords).toString(2))
            writeEntry(TAGS, BackupJsonCodec.tagsToJson(data.tags).toString(2))
            writeEntry(
                ACHIEVEMENTS,
                BackupJsonCodec.achievementsToJson(data.achievements).toString(2)
            )
            writeEntry(SETTINGS, BackupJsonCodec.settingsToJson(data.settings).toString(2))
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
            settings = BackupJsonCodec.parseSettings(entries.getValue(SETTINGS).toUtf8())
        ).also(::validate)
    }

    private fun validate(data: BackupData) {
        require(data.flights.map(Flight::id).distinct().size == data.flights.size) {
            "飞行记录 ID 重复"
        }
        data.flights.forEach { flight ->
            require(flight.id >= 0L) { "飞行记录 ID 无效" }
            require(flight.startTime > 0L && flight.endTime >= flight.startTime) {
                "飞行记录时间无效"
            }
            require(flight.durationSeconds in 1..86_400) { "飞行时长超出范围" }
            require(
                abs((flight.endTime - flight.startTime) / 1_000L - flight.durationSeconds) <= 1L
            ) { "飞行记录时间与时长不一致" }
            require(flight.spurtCount?.let { it in 1..1_000 } != false) { "股数超出范围" }
            require(flight.semenVolumeMl?.let { it in 0.1f..1_000f } != false) {
                "射精量超出范围"
            }
            require(flight.spurtCount != null || flight.semenVolumeMl != null) {
                "飞行记录缺少射精量"
            }
            require(
                flight.volumeInputMode in RecordVolumeMode.entries.map { it.storedValue }
            ) { "飞行记录录入单位无效" }
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
            require(flight.moodNote.length <= 10_000) { "备注过长" }
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
            require(it.note.length <= 10_000) { "长度备注过长" }
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
            require(it.achievementKey in KNOWN_ACHIEVEMENTS) { "包含未知成就" }
            require(it.unlockedAt > 0L) { "成就时间无效" }
        }

        validateSettings(data.settings)
    }

    private fun validateSettings(settings: SettingsSnapshot) {
        require(UsernamePolicy.isWithinLimit(settings.username)) { "用户名过长" }
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
        JSONArray(settings.homeCardOrder)
        JSONObject(settings.homeCardVisibility)
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
