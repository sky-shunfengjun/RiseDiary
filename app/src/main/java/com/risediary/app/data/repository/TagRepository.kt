package com.risediary.app.data.repository

import androidx.room.withTransaction
import com.risediary.app.data.AppDatabase
import com.risediary.app.data.dao.TagDao
import com.risediary.app.data.entity.Tag
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface TagRepository {
    val allTags: Flow<List<Tag>>

    suspend fun insert(tag: Tag): Long
    suspend fun update(tag: Tag)
    suspend fun updateAll(tags: List<Tag>)
    suspend fun delete(tag: Tag)
    suspend fun getAll(): List<Tag>
    suspend fun getById(id: Long): Tag?
    suspend fun getByName(name: String): Tag?
    suspend fun count(): Int
}

class RoomTagRepository @Inject constructor(
    private val dao: TagDao,
    private val database: AppDatabase
) : TagRepository {
    override val allTags: Flow<List<Tag>> = dao.getAllFlow()
    override suspend fun insert(tag: Tag): Long = dao.insert(tag)
    override suspend fun update(tag: Tag) {
        database.withTransaction {
            val previous = dao.getById(tag.id)
            dao.update(tag)
            if (previous != null && previous.name != tag.name) {
                database.flightDao().getAll().forEach { flight ->
                    renameTagReferences(flight.methodTags, previous.name, tag.name)?.let { tags ->
                        database.flightDao().update(flight.copy(methodTags = tags))
                    }
                }
            }
        }
    }
    override suspend fun updateAll(tags: List<Tag>) = dao.updateAll(tags)
    override suspend fun delete(tag: Tag) = dao.delete(tag)
    override suspend fun getAll(): List<Tag> = dao.getAll()
    override suspend fun getById(id: Long): Tag? = dao.getById(id)
    override suspend fun getByName(name: String): Tag? = dao.getByName(name)
    override suspend fun count(): Int = dao.count()
}

internal fun renameTagReferences(json: String, oldName: String, newName: String): String? {
    val tags = TagJson.decode(json)
    val renamed = renameTagNames(tags, oldName, newName) ?: return null
    return TagJson.encode(renamed)
}

internal fun renameTagNames(tags: List<String>, oldName: String, newName: String): List<String>? =
    if (oldName !in tags) null
    else tags.map { name -> if (name == oldName) newName else name }
