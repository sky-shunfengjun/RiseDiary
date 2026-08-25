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
                Flight(
                    id = getLong("id"),
                    startTime = getLong("startTime"),
                    endTime = getLong("endTime"),
                    durationSeconds = getInt("durationSeconds"),
                    spurtCount = spurtCount,
                    semenVolumeMl = semenVolumeMl,
                    volumeInputMode = if (has("volumeInputMode")) {
                        RecordVolumeMode.fromStoredValue(optString("volumeInputMode")).storedValue
                    } else {
                        RecordVolumeMode.inferLegacy(spurtCount, semenVolumeMl).storedValue
                    },
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
