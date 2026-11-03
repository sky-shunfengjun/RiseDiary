package com.risediary.app.data.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "library_folders", indices = [Index("parentId")])
@Serializable
data class LibraryFolder(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "library_assets", indices = [Index("folderId"), Index("sha256")])
@Serializable
data class LibraryAsset(
    @PrimaryKey val id: String,
    val folderId: String?,
    val mediaType: String,
    val originalName: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMillis: Long?,
    val sha256: String,
    val coverFrameMillis: Long?,
    val createdAt: Long,
    val state: String
)

enum class LibraryViewMode { GRID, LIST }
