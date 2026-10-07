package com.risediary.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.datastore.preferences.core.Preferences
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.UserPreferences
import com.risediary.app.data.entity.*
import com.risediary.app.data.sync.RecordIdentity
import com.risediary.app.service.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Private staging owns previews; the durable journal alone owns crash recovery. */
internal class BackupRestoreWorkflow(private val context: Context, private val db: AppDatabase,
    private val preferences: UserPreferences, private val timerStore: TimerSessionStore,
    private val timerHolder: TimerStateHolder, private val journal: BackupRecoveryJournal,
    private val validate: (BackupData) -> Unit) {
    private val mutex = Mutex()
    private val root = File(context.cacheDir, "restore-previews")
    private var active: Preparation? = null
    private data class Preparation(val id: String, val directory: File,
        val imported: BackupStagingStore, val result: BackupStagingStore, val settings: SettingsSnapshot,
        var revision: Long = 0, var fingerprint: ByteArray = byteArrayOf(),
        var importedFingerprint: ByteArray = byteArrayOf(), var resultFingerprint: ByteArray = byteArrayOf(),
        var preview: RestorePreview? = null)
    init {
        check(root.mkdirs() || root.isDirectory) { "无法创建恢复临时目录" }
        // These files cannot be reused after process death; none is the recovery journal.
        root.listFiles()?.forEach { file ->
            if (file.canonicalFile.parentFile == root.canonicalFile) file.deleteRecursively()
        }
    }
    private fun requireSpace(bytes: Long = 0) = requireBackupSpace(context.filesDir.usableSpace,bytes)
    private fun requireTimerReady(persisted: TimerSession) {
        // A successful service retry publishes the live session before clearing its error.
        // Read the error first so an idle snapshot cannot straddle that acknowledgement.
        val failed = timerHolder.persistenceError.value
        requireNoActiveTimerForMaintenance(persisted, timerHolder.state.value, failed)
    }
    private suspend fun source(): RecoverySource {
        val raw = preferences.rawSnapshot(); val timer = timerStore.load()
        val owner: CoroutineContext = currentCoroutineContext()[Job] ?: EmptyCoroutineContext
        return object : RecoverySource {
            override val preferences = raw
            override val timer = timer
            override fun flights(emit: (Flight) -> Unit) = batches(db.flightDao()::backupSizes, db.flightDao()::backupBatch, Flight::id, emit,owner)
            override fun lengths(emit: (LengthRecord) -> Unit) = batches(db.lengthRecordDao()::backupSizes, db.lengthRecordDao()::backupBatch, LengthRecord::id, emit,owner)
            override fun tags(emit: (Tag) -> Unit) = batches(db.tagDao()::backupSizes, db.tagDao()::backupBatch, Tag::id, emit,owner)
            override fun achievements(emit: (Achievement) -> Unit) = batches(db.achievementDao()::backupSizes, db.achievementDao()::backupBatch, Achievement::id, emit,owner)
            override fun drafts(emit: (com.risediary.app.data.draft.RecordDraftEntity) -> Unit) = runBlocking(owner) {
                var after = ""
                while (true) {
                    val sizes = db.recordDraftDao().backupSizes(after)
                    if (sizes.isEmpty()) break
                    val rows = db.recordDraftDao().backupBatch(after,backupBatchSize(sizes))
                    if (rows.isEmpty()) break
                    rows.forEach { emit(it); after = it.draftId }
                }
            }
        }
    }
    private fun <T> batches(sizes: suspend (Long) -> List<Long>, load: suspend (Long,Int) -> List<T>, id: (T) -> Long, emit: (T) -> Unit, owner: CoroutineContext) = runBlocking(owner) {
        var after = -1L
        while (true) {
            val weights = sizes(after)
            if (weights.isEmpty()) break
            val rows = load(after,backupBatchSize(weights))
            if (rows.isEmpty()) break
            rows.forEach { emit(it); after = id(it) }
        }
    }
    private fun flightRow(flight: Flight, explicit: Boolean = true) = StagedRow(BackupFiles.FLIGHTS, flight.id,
        BackupJsonCodec.flightToJson(flight).toString(), flight.globalId.takeIf { explicit }, flight.startTime,flight.createdAt,flight.recordDraftId)
    private fun lengthRow(length: LengthRecord, explicit: Boolean = true) = StagedRow(BackupFiles.LENGTHS,length.id,
        BackupJsonCodec.lengthToJson(length).toString(),length.globalId.takeIf { explicit },length.recordDate)
    private fun flight(row: StagedRow) = BackupJsonCodec.parseFlights("[" + row.payload + "]").single()
    private fun length(row: StagedRow) = BackupJsonCodec.parseLengths("[" + row.payload + "]").single()

    suspend fun prepare(uri: Uri, mode: RestoreMode): RestorePreview = mutex.withLock {
        cleanup()
        requireSpace()
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir()) { "无法创建恢复临时目录" }
        val stores = mutableListOf<BackupStagingStore>()
        try {
            val imported = BackupStagingStore(File(directory,"imported.db")).also(stores::add)
            val result = BackupStagingStore(File(directory,"result.db")).also(stores::add)
            val settings = context.contentResolver.openInputStream(uri)?.use { input ->
                BackupStreamReader.read(input,imported,directory) { kind,row ->
                    BackupJsonCodec.requireStrictFields(row,kind)
                    val defaults = defaultBackupSettings()
                    when (kind) {
                        BackupFiles.FLIGHTS -> {
                            val parsed = BackupJsonCodec.parseFlights("[$row]").single()
                            validate(BackupData(listOf(parsed),emptyList(),emptyList(),emptyList(),defaults))
                            flightRow(parsed,row.has("globalId"))
                        }
                        BackupFiles.LENGTHS -> {
                            val parsed = BackupJsonCodec.parseLengths("[$row]").single()
                            validate(BackupData(emptyList(),listOf(parsed),emptyList(),emptyList(),defaults))
                            require(RecordIdentity.isValidId(parsed.globalId)) { "长度记录固定编号无效" }
                            lengthRow(parsed,row.has("globalId"))
                        }
                        BackupFiles.TAGS -> {
                            val parsed = BackupJsonCodec.parseTags("[$row]").single()
                            validate(BackupData(emptyList(),emptyList(),listOf(parsed),emptyList(),defaults))
                            StagedRow(kind,parsed.id,BackupJsonCodec.tagToJson(parsed).toString(),parsed.name.lowercase(java.util.Locale.ROOT))
                        }
                        else -> {
                            val parsed = BackupJsonCodec.parseAchievements("[$row]").single()
                            validate(BackupData(emptyList(),emptyList(),emptyList(),listOf(parsed),defaults))
                            StagedRow(kind,parsed.id,BackupJsonCodec.achievementToJson(parsed).toString(),parsed.achievementKey)
                        }
                    }
                }
            } ?: error("无法读取所选备份")
            validate(BackupData(emptyList(),emptyList(),emptyList(),emptyList(),settings))
            val preparation = Preparation(directory.name,directory,imported,result,settings)
            preparation.importedFingerprint = stageFingerprint(imported,settings)
            active = preparation
            preferences.maintenanceGate.write { calculate(preparation,mode) }
        } catch (failure: Throwable) {
            stores.forEach { runCatching { it.close() } }
            runCatching { directory.deleteRecursively() }; active = null
            throw failure
        }
    }
    suspend fun change(id: String, mode: RestoreMode): RestorePreview = mutex.withLock {
        val preparation = requireActive(id)
        preferences.maintenanceGate.write { calculate(preparation,mode) }
    }
    suspend fun discard(id: String) = mutex.withLock { if (active?.id == id) cleanup() }
    private fun requireActive(id: String) = checkNotNull(active?.takeIf { it.id == id }) { "预览已失效，请重新选择备份" }
    private fun cleanup() {
        val previous = active
        active = null
        previous?.let {
            runCatching { it.imported.close() }; runCatching { it.result.close() }
            runCatching { it.directory.deleteRecursively() }
        }
    }
    private suspend fun calculate(preparation: Preparation, mode: RestoreMode): RestorePreview {
        preparation.preview = null
        require(MessageDigest.isEqual(preparation.importedFingerprint,stageFingerprint(preparation.imported,preparation.settings))) {
            "临时备份已变化，请取消后重新选择"
        }
        requireSpace()
        val original = source()
        requireTimerReady(original.timer)
        val localSettings = preferences.settingsSnapshot(original.preferences)
        val result = preparation.result
        result.clear()
        var currentFlights = 0; var currentLengths = 0
        original.flights { currentFlights++; if (mode == RestoreMode.MERGE) result.put(flightRow(it)) }
        original.lengths { currentLengths++; if (mode == RestoreMode.MERGE) result.put(lengthRow(it)) }
        preparation.imported.each(BackupFiles.FLIGHTS) { incoming ->
            requireSpace()
            val local = if (mode == RestoreMode.MERGE) result.match(incoming) else null
            if (mode == RestoreMode.MERGE && incoming.submission != null) {
                val bound = result.submission(incoming.submission)
                require(bound == null || bound.id == local?.id) { "记录提交关联冲突，未恢复数据" }
                val cached = db.recordDraftDao().getById(incoming.submission)
                require(cached == null || (local != null && cached.completedFlightId == local.id)) { "记录提交缓存冲突，未恢复数据" }
            }
            val backup = flight(incoming)
            val merged = RestoreIdentityPolicy.mergeFlight(backup,local?.let(::flight)).let {
                if (local == null) it.copy(id = Math.addExact(result.maxId(incoming.kind),1)) else it
            }
            val row = flightRow(merged)
            result.put(row, local != null)
            result.action(row.kind,row.id, if (local == null) "added" else if (row.payload == local.payload) "skipped" else "updated")
        }
        preparation.imported.each(BackupFiles.LENGTHS) { incoming ->
            requireSpace()
            val local = if (mode == RestoreMode.MERGE) result.match(incoming) else null
            val merged = RestoreIdentityPolicy.mergeLength(length(incoming),local?.let(::length)).let {
                if (local == null) it.copy(id = Math.addExact(result.maxId(incoming.kind),1)) else it
            }
            val row = lengthRow(merged)
            result.put(row,local != null)
            result.action(row.kind,row.id,if (local == null) "added" else if (row.payload == local.payload) "skipped" else "updated")
        }
        if (mode==RestoreMode.MERGE) {
            for (kind in listOf(BackupFiles.FLIGHTS,BackupFiles.LENGTHS)) result.each(kind) { row ->
                if (result.actionCountFor(kind,row.id).isNotEmpty()) {
                    val originalPayload = if(kind==BackupFiles.FLIGHTS) db.flightDao().getById(row.id)?.let { flightRow(it).payload }
                        else db.lengthRecordDao().getById(row.id)?.let { lengthRow(it).payload }
                    result.action(kind,row.id,restoreRecordAction(originalPayload,row.payload))
                }
            }
        }
        for (kind in listOf(BackupFiles.TAGS,BackupFiles.ACHIEVEMENTS)) preparation.imported.each(kind) { result.put(it) }
        // The full result, including phone-only rows, must remain exportable.
        writeStage(DiscardOutput,result,preparation.settings)
        preparation.resultFingerprint = stageFingerprint(result,preparation.settings)
        preparation.fingerprint = journal.fingerprint(original)
        preparation.revision = Math.addExact(preparation.revision,1)
        fun counts(kind: String,current: Int) = RestoreCounts(result.actionCount(kind,"added"),
            result.actionCount(kind,"updated"),result.actionCount(kind,"skipped"),current,
            preparation.imported.count(kind),result.count(kind))
        val before = BackupJsonCodec.settingsToJson(localSettings)
        val after = BackupJsonCodec.settingsToJson(preparation.settings)
        val changes = after.keys().asSequence().filter { it != "onboarding_completed" && before.opt(it) != after.opt(it) }
            .map { key -> settingLabel(key) + "：" + settingDisplay(key,before.opt(key)) + " → " + settingDisplay(key,after.opt(key)) }.toList()
        return RestorePreview(preparation.id,preparation.revision,mode,counts(BackupFiles.FLIGHTS,currentFlights),
            counts(BackupFiles.LENGTHS,currentLengths),changes).also { preparation.preview = it }
    }
    suspend fun confirm(id: String, revision: Long): RestoreConfirmation = mutex.withLock {
        val preparation = requireActive(id)
        val preview = checkNotNull(preparation.preview)
        if (revision != preparation.revision) return@withLock RestoreConfirmation.Changed(preview)
        var checkedOriginal: RecoverySource? = null
        val answer = preferences.maintenanceGate.preparedMaintenance<RestoreConfirmation>(precheck = {
            require(MessageDigest.isEqual(preparation.resultFingerprint,stageFingerprint(preparation.result,preparation.settings))) {
                "恢复临时文件已变化，请取消后重新选择"
            }
            val original = source()
            requireTimerReady(original.timer)
            if (!MessageDigest.isEqual(preparation.fingerprint,journal.fingerprint(original)))
                RestoreConfirmation.Changed(calculate(preparation,preview.mode))
            else { checkedOriginal = original; null }
        }) {
            val original = checkNotNull(checkedOriginal)
            val required = journal.estimatedBytes(original) + preparation.result.file.length() * 2
            requireSpace(required)
            journal.persist(original)
            try {
                db.withTransaction {
                    if (preview.mode == RestoreMode.REPLACE) {
                        db.recordDraftDao().nuke(); db.flightDao().nuke(); db.lengthRecordDao().nuke()
                    }
                    preparation.result.each(BackupFiles.FLIGHTS) { row ->
                        if (preview.mode == RestoreMode.REPLACE || db.flightDao().getById(row.id) == null) db.flightDao().insertNew(flight(row))
                        else if (preparation.result.actionCountFor(row.kind,row.id) != "") db.flightDao().update(flight(row))
                    }
                    preparation.result.each(BackupFiles.LENGTHS) { row ->
                        if (preview.mode == RestoreMode.REPLACE || db.lengthRecordDao().getById(row.id) == null) db.lengthRecordDao().insertNew(length(row))
                        else if (preparation.result.actionCountFor(row.kind,row.id) != "") db.lengthRecordDao().update(length(row))
                    }
                    db.tagDao().nuke(); db.achievementDao().nuke()
                    preparation.result.each(BackupFiles.TAGS) { db.tagDao().insert(BackupJsonCodec.parseTags("["+it.payload+"]").single()) }
                    preparation.result.each(BackupFiles.ACHIEVEMENTS) { db.achievementDao().insert(BackupJsonCodec.parseAchievements("["+it.payload+"]").single()) }
                }
                preferences.applySettingsForMaintenance(preparation.settings)
                timerStore.save(TimerSession()); timerHolder.set(TimerSession())
                journal.clear()
                RestoreConfirmation.Finished(BackupResult.Success("数据恢复成功"))
            } catch (failure: Throwable) {
                val rollback = runCatching { restoreJournal(); journal.clear() }
                if (rollback.isFailure) preferences.maintenanceGate.requireRecovery()
                RestoreConfirmation.Finished(BackupResult.Failure(if (rollback.isSuccess) "恢复失败，已还原原数据："+(failure.message ?: "请重试")
                    else "原数据尚未完整还原，当前暂时只读，请重试还原。",failure))
            }
        }
        if (answer is RestoreConfirmation.Finished) cleanup()
        answer
    }
    suspend fun clearAll(): BackupResult = mutex.withLock {
        preferences.maintenanceGate.maintenance {
            val original = source()
            requireTimerReady(original.timer)
            requireSpace(journal.estimatedBytes(original))
            journal.persist(original)
            try {
                db.withTransaction {
                    db.recordDraftDao().nuke(); db.flightDao().nuke(); db.lengthRecordDao().nuke()
                    db.tagDao().nuke(); db.achievementDao().nuke()
                    com.risediary.app.data.SeedData.defaultTags.forEach { db.tagDao().insert(it) }
                }
                preferences.applySettingsForMaintenance(defaultBackupSettings())
                preferences.clearAppLockForMaintenance()
                timerStore.save(TimerSession()); timerHolder.set(TimerSession())
                journal.clear()
                BackupResult.Success("所有数据已清除")
            } catch (failure: Throwable) {
                val rollback = runCatching { restoreJournal(); journal.clear() }
                if (rollback.isFailure) preferences.maintenanceGate.requireRecovery()
                BackupResult.Failure(if (rollback.isSuccess) "清除失败，已还原原数据" else "原数据尚未完整还原，请重试还原",failure)
            }
        }
    }

    suspend fun restoreJournal() {
        var raw: Preferences? = null; var restoredTimer: TimerSession? = null
        db.withTransaction {
            db.recordDraftDao().nuke(); db.flightDao().nuke(); db.lengthRecordDao().nuke(); db.tagDao().nuke(); db.achievementDao().nuke()
            journal.replay(object : RecoverySink {
                override suspend fun flight(row: Flight) { db.flightDao().insertNew(row) }
                override suspend fun length(row: LengthRecord) { db.lengthRecordDao().insertNew(row) }
                override suspend fun tag(row: Tag) { db.tagDao().insert(row) }
                override suspend fun achievement(row: Achievement) { db.achievementDao().insert(row) }
                override suspend fun draft(row: com.risediary.app.data.draft.RecordDraftEntity) { db.recordDraftDao().insert(row) }
                override suspend fun settings(preferences: Preferences, timer: TimerSession) { raw = preferences; restoredTimer = timer }
            })
        }
        preferences.restoreRaw(checkNotNull(raw)); timerStore.save(checkNotNull(restoredTimer)); timerHolder.set(checkNotNull(restoredTimer))
    }
    suspend fun export(output: OutputStream) = preferences.maintenanceGate.write {
        val source = source()
        val settings = preferences.settingsSnapshot(source.preferences)
        writeZip(output,settings) { kind,emit ->
            when (kind) {
                BackupFiles.FLIGHTS -> source.flights { emit(BackupJsonCodec.flightToJson(it.copy(updatedAt =
                    com.risediary.app.data.RecordTimestamps.updatedAt(it.createdAt,it.updatedAt,it.updatedAt))).toString()) }
                BackupFiles.LENGTHS -> source.lengths { emit(BackupJsonCodec.lengthToJson(it).toString()) }
                BackupFiles.TAGS -> source.tags { emit(BackupJsonCodec.tagToJson(it).toString()) }
                else -> source.achievements { emit(BackupJsonCodec.achievementToJson(it).toString()) }
            }
        }
    }
    private suspend fun writeStage(output: OutputStream,stage: BackupStagingStore,settings: SettingsSnapshot) =
        writeZip(output,settings) { kind,emit -> stage.each(kind) {
            if (kind == BackupFiles.FLIGHTS) {
                val row = flight(it)
                emit(BackupJsonCodec.flightToJson(row.copy(updatedAt = com.risediary.app.data.RecordTimestamps.updatedAt(
                    row.createdAt,row.updatedAt,row.updatedAt))).toString())
            } else emit(it.payload)
        } }
    private suspend fun writeZip(output: OutputStream, settings: SettingsSnapshot,
        array: suspend (String,(String)->Unit) -> Unit) {
        val budget = BackupSizeBudget()
        ZipOutputStream(output.buffered()).use { zip ->
            suspend fun entry(name: String, block: suspend ((String)->Unit)->Unit) {
                budget.beginEntry()
                zip.putNextEntry(ZipEntry(name))
                block { text ->
                    val buffer = text.toByteArray(Charsets.UTF_8)
                    budget.account(buffer.size)
                    zip.write(buffer)
                }
                zip.closeEntry()
            }
            for (kind in BackupFiles.arrays) entry(kind) { emit ->
                emit("["); var first = true
                array(kind) { text -> if (!first) emit(","); first = false; emit(text) }
                emit("]")
            }
            entry(BackupFiles.SETTINGS) { it(BackupJsonCodec.settingsToJson(settings).toString()) }
        }
    }
    private suspend fun stageFingerprint(stage: BackupStagingStore, settings: SettingsSnapshot): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val out = java.io.DataOutputStream(java.security.DigestOutputStream(DiscardOutput,digest))
        fun text(value: String?) {
            if (value == null) out.writeInt(-1)
            else { val bytes=value.toByteArray(Charsets.UTF_8); out.writeInt(bytes.size); out.write(bytes) }
        }
        for (kind in BackupFiles.arrays) {
            text(kind); out.writeInt(stage.count(kind))
            stage.each(kind) { row ->
                out.writeLong(row.id); text(row.payload); text(row.globalId)
                out.writeLong(row.firstTime); out.writeLong(row.secondTime); text(row.submission)
                text(stage.actionCountFor(kind,row.id))
            }
        }
        text(BackupJsonCodec.settingsToJson(settings).toString()); out.flush()
        return digest.digest()
    }

    private fun settingDisplay(key: String,value: Any?): String {
        if (key=="prediction_max_ticks" && value is Number) return com.risediary.app.util.PredictionQuantitySettings.formatTicks(value.toInt())+" 毫升"
        if (key=="ml_per_spurt") return "$value 毫升"
        if (key=="inactive_reminder_days") return "$value 天"
        if (key=="monthly_length_reminder_day") return "每月 $value 日"
        if (key=="home_card_order" || key=="home_card_visibility") return if(value=="[]" || value=="{}") "默认" else "自定义"
        return when(value) {
            true -> "开启"; false -> "关闭"; "system" -> "跟随系统"; "light" -> "浅色"; "dark" -> "深色"
            "milliliters" -> "毫升"; "spurts" -> "旧股数"; "always" -> "切到后台即锁定"
            "except_while_timer_active" -> "计时期间保持解锁"
            else -> value.toString()
        }
    }
    private fun settingLabel(key: String) = mapOf("username" to "称呼", "prediction_max_ticks" to "预测上限",
        "live_updates_enabled" to "实时通知", "detail_video_hidden_by_default" to "详情视频默认隐藏",
        "theme_mode" to "主题", "ml_per_spurt" to "旧数量换算值", "default_volume_mode" to "数量录入",
        "daily_reminder_enabled" to "每日提醒", "daily_reminder_time" to "每日提醒时间",
        "inactive_reminder_enabled" to "未记录提醒", "inactive_reminder_days" to "未记录天数",
        "inactive_reminder_time" to "未记录提醒时间", "monthly_length_reminder_enabled" to "长度提醒",
        "monthly_length_reminder_day" to "长度提醒日期", "monthly_length_reminder_time" to "长度提醒时间",
        "reminder_sound" to "提醒声音", "reminder_vibration" to "提醒振动", "background_auto_lock_enabled" to "后台自动锁定",
        "background_lock_mode" to "后台锁定方式", "home_card_order" to "首页排序", "home_card_visibility" to "首页卡片显示")[key] ?: key
}
