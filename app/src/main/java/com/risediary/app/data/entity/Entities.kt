package com.risediary.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecordVolumeMode(val storedValue: String) {
    MILLILITERS("milliliters"),
    SPURTS("spurts");

    companion object {
        fun fromStoredValue(value: String?): RecordVolumeMode =
            entries.firstOrNull { it.storedValue == value } ?: MILLILITERS

        fun inferLegacy(spurtCount: Int?, semenVolumeMl: Float?): RecordVolumeMode =
            if (spurtCount != null && semenVolumeMl == null) SPURTS else MILLILITERS
    }
}

@Entity(
    tableName = "flights",
    indices = [Index(value = ["startTime"])]
)
data class Flight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val spurtCount: Int?,
    val semenVolumeMl: Float?,
    val volumeInputMode: String = RecordVolumeMode.MILLILITERS.storedValue,
    val ejaculationDistanceCm: Float?,
    val methodTags: String,
    val moodNote: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "length_records",
    indices = [Index(value = ["recordDate"])]
)
data class LengthRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordDate: Long,
    val flaccidLengthCm: Float,
    val erectLengthCm: Float,
    val note: String = ""
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "achievements",
    indices = [Index(value = ["achievementKey"], unique = true)]
)
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achievementKey: String,
    val unlockedAt: Long = System.currentTimeMillis(),
    val notified: Boolean = false
)
