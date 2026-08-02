package com.risediary.app.data.dao

import androidx.room.*
import com.risediary.app.data.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: Achievement): Long

    @Update
    suspend fun update(achievement: Achievement)

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun getAllFlow(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    suspend fun getAll(): List<Achievement>

    @Query("SELECT * FROM achievements WHERE achievementKey = :key LIMIT 1")
    suspend fun getByKey(key: String): Achievement?

    @Query("SELECT * FROM achievements WHERE notified = 0 ORDER BY unlockedAt DESC LIMIT 1")
    suspend fun getLatestUnnotified(): Achievement?

    @Query("SELECT * FROM achievements WHERE notified = 0 ORDER BY unlockedAt ASC")
    suspend fun getAllUnnotified(): List<Achievement>

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun count(): Int

    @Query("DELETE FROM achievements")
    suspend fun nuke()
}
