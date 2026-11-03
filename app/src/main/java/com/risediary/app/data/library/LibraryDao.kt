package com.risediary.app.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_assets")
    fun observeAllAssets(): Flow<List<LibraryAsset>>
    @Query("SELECT * FROM library_folders")
    fun observeAllFolders(): Flow<List<LibraryFolder>>
    @Insert suspend fun insertFolder(folder: LibraryFolder)
    @Update suspend fun updateAssets(assets: List<LibraryAsset>)
    @Update suspend fun updateFolders(folders: List<LibraryFolder>)
    @Query("DELETE FROM library_folders WHERE id IN (:ids)")
    suspend fun deleteFolders(ids: List<String>)
    @Query("SELECT * FROM library_assets WHERE state = 'deleting'")
    suspend fun pendingCleanup(): List<LibraryAsset>
    @Query("DELETE FROM library_assets WHERE id = :id AND state = 'deleting'")
    suspend fun finishCleanup(id: String)
    @Query("SELECT COUNT(*) FROM library_assets WHERE folderId IS :folderId AND state = 'ready' AND (createdAt > :createdAt OR (createdAt = :createdAt AND id < :id))")
    suspend fun position(folderId: String?, createdAt: Long, id: String): Int
    @Query("SELECT * FROM library_assets WHERE folderId IS :folderId AND state = 'ready' ORDER BY createdAt DESC, id LIMIT :limit")
    fun observeAssets(folderId: String?, limit: Int): Flow<List<LibraryAsset>>

    @Query("SELECT * FROM library_assets WHERE sha256 = :hash AND state = 'ready' LIMIT 1")
    suspend fun byHash(hash: String): LibraryAsset?

    @Query("SELECT name FROM library_assets WHERE folderId IS :folderId UNION SELECT name FROM library_folders WHERE parentId IS :folderId")
    suspend fun names(folderId: String?): List<String>

    @Query("SELECT * FROM library_assets ORDER BY createdAt DESC, id")
    suspend fun allAssets(): List<LibraryAsset>

    @Query("SELECT * FROM library_folders ORDER BY createdAt, id")
    suspend fun allFolders(): List<LibraryFolder>

    @Insert
    suspend fun insert(asset: LibraryAsset)

    @Query("SELECT * FROM library_folders WHERE parentId IS :parentId ORDER BY name, id")
    fun observeFolders(parentId: String?): Flow<List<LibraryFolder>>

    @Query("SELECT * FROM library_assets WHERE folderId IS :folderId ORDER BY createdAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun assets(folderId: String?, limit: Int = 100, offset: Int = 0): List<LibraryAsset>

    @Query("SELECT * FROM library_assets WHERE id = :id")
    suspend fun asset(id: String): LibraryAsset?
}
