package com.example.notebook.data.db.dao

import androidx.room.*
import com.example.notebook.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentId IS NULL ORDER BY updatedAt DESC")
    fun observeRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY updatedAt DESC")
    fun observeChildFolders(parentId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)

    @Query("""
        WITH RECURSIVE ancestors(id, name, parentId, colorHex, createdAt, updatedAt, depth) AS (
            SELECT id, name, parentId, colorHex, createdAt, updatedAt, 0 FROM folders WHERE id = :folderId
            UNION ALL
            SELECT f.id, f.name, f.parentId, f.colorHex, f.createdAt, f.updatedAt, a.depth + 1
            FROM folders f
            INNER JOIN ancestors a ON f.id = a.parentId
        )
        SELECT id, name, parentId, colorHex, createdAt, updatedAt FROM ancestors ORDER BY depth DESC
    """)
    suspend fun getAncestors(folderId: Long): List<FolderEntity>
}