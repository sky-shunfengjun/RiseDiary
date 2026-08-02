package com.risediary.app.data.dao

import androidx.room.*
import com.risediary.app.data.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Update
    suspend fun updateAll(tags: List<Tag>)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC")
    fun getAllFlow(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Tag?

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Tag?

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("DELETE FROM tags")
    suspend fun nuke()
}
