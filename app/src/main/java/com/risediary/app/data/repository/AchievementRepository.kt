package com.risediary.app.data.repository

import com.risediary.app.data.dao.AchievementDao
import com.risediary.app.data.entity.Achievement
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.risediary.app.data.DataMaintenanceGate

interface AchievementRepository {
    val allAchievements: Flow<List<Achievement>>

    suspend fun insert(achievement: Achievement): Long
    suspend fun update(achievement: Achievement)
    suspend fun getByKey(key: String): Achievement?
    suspend fun getAll(): List<Achievement>
    suspend fun getLatestUnnotified(): Achievement?
    suspend fun getAllUnnotified(): List<Achievement>
    suspend fun unlock(key: String): Achievement?
    suspend fun markNotified(key: String)
}

class RoomAchievementRepository @Inject constructor(
    private val dao: AchievementDao,
    private val maintenanceGate: DataMaintenanceGate = DataMaintenanceGate()
) : AchievementRepository {
    override val allAchievements: Flow<List<Achievement>> = dao.getAllFlow()

    override suspend fun insert(achievement: Achievement): Long = maintenanceGate.write { dao.insert(achievement) }
    override suspend fun update(achievement: Achievement) = maintenanceGate.write { dao.update(achievement) }
    override suspend fun getByKey(key: String): Achievement? = dao.getByKey(key)
    override suspend fun getAll(): List<Achievement> = dao.getAll()
    override suspend fun getLatestUnnotified(): Achievement? = dao.getLatestUnnotified()
    override suspend fun getAllUnnotified(): List<Achievement> = dao.getAllUnnotified()

    override suspend fun unlock(key: String): Achievement? = maintenanceGate.write {
        val achievement = Achievement(achievementKey = key)
        if (dao.insert(achievement) == -1L) null else achievement
    }

    override suspend fun markNotified(key: String) = maintenanceGate.write {
        val achievement = getByKey(key) ?: return@write
        dao.update(achievement.copy(notified = true))
    }
}
