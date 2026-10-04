package com.risediary.app.data.backup

import com.risediary.app.data.BackgroundLockMode
import com.risediary.app.data.DefaultVolumeMode
import com.risediary.app.data.entity.Achievement
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.entity.LengthRecord
import com.risediary.app.data.entity.RecordVolumeMode
import com.risediary.app.data.entity.Tag
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal object BackupJsonCodec {
    fun flightToJson(value: Flight) = JSONObject().apply {
        put("id", value.id)
        put("startTime", value.startTime)
        put("endTime", value.endTime)
        put("durationSeconds", value.durationSeconds)
        put("spurtCount", value.spurtCount ?: JSONObject.NULL)
        put("semenVolumeMl", value.semenVolumeMl ?: JSONObject.NULL)
        put("volumeInputMode", value.volumeInputMode)
        put("legacySpurtCount", value.legacySpurtCount ?: JSONObject.NULL)
        put("legacyVolumeMl", value.legacyVolumeMl ?: JSONObject.NULL)
        put("legacyVolumeInputMode", value.legacyVolumeInputMode ?: JSONObject.NULL)
        put("predictionMaxTicks", value.predictionMaxTicks ?: JSONObject.NULL)
        put("videoUri", value.videoUri ?: JSONObject.NULL)
        put("videoDisplayName", value.videoDisplayName ?: JSONObject.NULL)
        put("videoMimeType", value.videoMimeType ?: JSONObject.NULL)
        put("recordDraftId", value.recordDraftId ?: JSONObject.NULL)
        put("timingSource", value.timingSource)
        put("ejaculationDistanceCm", value.ejaculationDistanceCm ?: JSONObject.NULL)
        put("methodTags", value.methodTags)
        put("moodNote", value.moodNote)
        put("createdAt", value.createdAt)
        put("updatedAt", value.updatedAt)
    }

    fun lengthToJson(value: LengthRecord) = JSONObject().apply {
        put("id", value.id)
        put("recordDate", value.recordDate)
        put("flaccidLengthCm", value.flaccidLengthCm)
        put("erectLengthCm", value.erectLengthCm)
        put("note", value.note)
    }

    fun tagToJson(value: Tag) = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("color", value.color)
        put("sortOrder", value.sortOrder)
    }

    fun achievementToJson(value: Achievement) = JSONObject().apply {
        put("id", value.id)
        put("achievementKey", value.achievementKey)
        put("unlockedAt", value.unlockedAt)
        put("notified", if (value.notified) 1 else 0)
    }

    fun flightsToJson(values: List<Flight>) = JSONArray().apply {
        values.forEach { put(flightToJson(it)) }
    }

    fun lengthsToJson(values: List<LengthRecord>) = JSONArray().apply {
        values.forEach { put(lengthToJson(it)) }
    }

    fun tagsToJson(values: List<Tag>) = JSONArray().apply {
        values.forEach { put(tagToJson(it)) }
    }

    fun achievementsToJson(values: List<Achievement>) = JSONArray().apply {
        values.forEach { put(achievementToJson(it)) }
    }

    fun settingsToJson(value: SettingsSnapshot) = JSONObject().apply {
        put("username", value.username)
        put("prediction_max_ticks", value.predictionMaxTicks)
        put("ml_per_spurt", value.mlPerSpurt)
        put("default_volume_mode", value.defaultVolumeMode.storedValue)
        put("daily_reminder_enabled", value.dailyReminderEnabled)
        put("daily_reminder_time", value.dailyReminderTime)
        put("inactive_reminder_enabled", value.inactiveReminderEnabled)
        put("inactive_reminder_days", value.inactiveReminderDays)
        put("inactive_reminder_time", value.inactiveReminderTime)
        put("monthly_length_reminder_enabled", value.monthlyLengthReminderEnabled)
        put("monthly_length_reminder_day", value.monthlyLengthReminderDay)
        put("monthly_length_reminder_time", value.monthlyLengthReminderTime)
        put("reminder_sound", value.reminderSound)
        put("reminder_vibration", value.reminderVibration)
        put("background_auto_lock_enabled", value.backgroundAutoLockEnabled)
        put("background_lock_mode", value.backgroundLockMode.storedValue)
        put("theme_mode", value.themeMode)
        put("home_card_order", value.homeCardOrder)
        put("home_card_visibility", value.homeCardVisibility)
        put("onboarding_completed", value.onboardingCompleted)
    }

    fun parseFlights(json: String): List<Flight> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            array.getJSONObject(index).run {
                val spurtCount = if (isNull("spurtCount")) null else getInt("spurtCount")
                val semenVolumeMl = if (isNull("semenVolumeMl")) null
                    else getDouble("semenVolumeMl").toFloat()
                val mode = if (has("volumeInputMode")) {
                    val stored = getString("volumeInputMode")
                    require(stored in RecordVolumeMode.entries.map { it.storedValue }) { "飞行记录录入模式无效" }
                    stored
                } else RecordVolumeMode.inferLegacy(spurtCount, semenVolumeMl).storedValue
                val videoUri = videoText("videoUri")
                val videoName = videoText("videoDisplayName")
                val videoMime = videoText("videoMimeType")
                require(com.risediary.app.media.validateLocalVideoFields(videoUri, videoName, videoMime) == null) {
                    "视频关联信息无效"
                }
                val historyKeys = listOf("legacySpurtCount", "legacyVolumeMl", "legacyVolumeInputMode")
                val hasHistoryFields = historyKeys.any { has(it) }
                require(!hasHistoryFields || historyKeys.all { has(it) }) { "原数量信息不完整" }
                require(hasHistoryFields || mode != RecordVolumeMode.ESTIMATED.storedValue) { "预测记录信息不完整" }
                Flight(
                    id = getLong("id"),
                    startTime = getLong("startTime"),
                    endTime = getLong("endTime"),
                    durationSeconds = getInt("durationSeconds"),
                    spurtCount = spurtCount,
                    semenVolumeMl = semenVolumeMl,
                    volumeInputMode = mode,
                    legacySpurtCount = if (!hasHistoryFields) spurtCount else
                        if (isNull("legacySpurtCount")) null else getInt("legacySpurtCount"),
                    legacyVolumeMl = if (!hasHistoryFields) semenVolumeMl else
                        if (isNull("legacyVolumeMl")) null else getDouble("legacyVolumeMl").toFloat(),
                    legacyVolumeInputMode = if (!hasHistoryFields) mode else
                        if (isNull("legacyVolumeInputMode")) null else getString("legacyVolumeInputMode"),
                    predictionMaxTicks = if (isNull("predictionMaxTicks")) null else strictTicks("predictionMaxTicks"),
                    videoUri = videoUri,
                    videoDisplayName = videoName,
                    videoMimeType = videoMime,
                    recordDraftId = videoText("recordDraftId")?.also { require(it.isNotBlank() && it.length <= 128) },
                    timingSource = if (has("timingSource")) requireNotNull(videoText("timingSource")) else "manual",
                    ejaculationDistanceCm = if (isNull("ejaculationDistanceCm")) null
                        else getDouble("ejaculationDistanceCm").toFloat(),
                    methodTags = getString("methodTags"),
                    moodNote = getString("moodNote"),
                    createdAt = getLong("createdAt"),
                    updatedAt = getLong("updatedAt")
                )
            }
        }
    }

    fun parseLengths(json: String): List<LengthRecord> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            array.getJSONObject(index).run {
                LengthRecord(
                    id = getLong("id"),
                    recordDate = getLong("recordDate"),
                    flaccidLengthCm = getDouble("flaccidLengthCm").toFloat(),
                    erectLengthCm = getDouble("erectLengthCm").toFloat(),
                    note = getString("note")
                )
            }
        }
    }

    fun parseTags(json: String): List<Tag> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            array.getJSONObject(index).run {
                Tag(
                    id = getLong("id"),
                    name = getString("name"),
                    color = getString("color"),
                    sortOrder = getInt("sortOrder")
                )
            }
        }
    }

    fun parseAchievements(json: String): List<Achievement> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            array.getJSONObject(index).run {
                Achievement(
                    id = getLong("id"),
                    achievementKey = getString("achievementKey"),
                    unlockedAt = getLong("unlockedAt"),
                    notified = getInt("notified") != 0
                )
            }
        }
    }

    fun parseSettings(json: String): SettingsSnapshot = JSONObject(json).run {
        SettingsSnapshot(
            predictionMaxTicks = if (has("prediction_max_ticks")) strictTicks("prediction_max_ticks") else 80,
            username = getString("username"),
            mlPerSpurt = getDouble("ml_per_spurt").toFloat(),
            defaultVolumeMode = parseBackupDefaultVolumeMode(
                optString("default_volume_mode", DefaultVolumeMode.MILLILITERS.storedValue)
            ),
            dailyReminderEnabled = getBoolean("daily_reminder_enabled"),
            dailyReminderTime = getString("daily_reminder_time"),
            inactiveReminderEnabled = getBoolean("inactive_reminder_enabled"),
            inactiveReminderDays = normalizeBackupInactiveDays(getInt("inactive_reminder_days")),
            inactiveReminderTime = optString("inactive_reminder_time", "22:00"),
            monthlyLengthReminderEnabled = getBoolean("monthly_length_reminder_enabled"),
            monthlyLengthReminderDay = getInt("monthly_length_reminder_day"),
            monthlyLengthReminderTime = optString("monthly_length_reminder_time", "22:00"),
            reminderSound = getBoolean("reminder_sound"),
            reminderVibration = getBoolean("reminder_vibration"),
            backgroundAutoLockEnabled = optBoolean("background_auto_lock_enabled", false),
            backgroundLockMode = BackgroundLockMode.fromStoredValue(
                optString("background_lock_mode", BackgroundLockMode.ALWAYS.storedValue)
            ),
            themeMode = getString("theme_mode"),
            homeCardOrder = getString("home_card_order"),
            homeCardVisibility = getString("home_card_visibility"),
            onboardingCompleted = getBoolean("onboarding_completed")
        )
    }

    private fun JSONObject.videoText(key: String): String? {
        if (isNull(key)) return null
        val raw = get(key)
        require(raw is String) { "视频信息格式无效" }
        return raw
    }

    private fun JSONObject.strictTicks(key: String): Int {
        val raw = get(key)
        require(raw is Int || raw is Long) { "预测最大值格式无效" }
        val ticks = (raw as Number).toLong()
        require(ticks in 1L..10_000L) { "预测最大值超出范围" }
        return ticks.toInt()
    }

    private fun normalizeBackupInactiveDays(value: Int): Int {
        require(value in 1..365) { "未记录提醒天数无效" }
        return listOf(3, 7, 14, 30).minBy { abs(it - value) }
    }

    private fun parseBackupDefaultVolumeMode(value: String): DefaultVolumeMode {
        require(value in DefaultVolumeMode.entries.map { it.storedValue }) {
            "默认记录单位无效"
        }
        return DefaultVolumeMode.fromStoredValue(value)
    }
}
