package com.example.notebook.data.db.dao

import androidx.room.*
import com.example.notebook.data.db.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun observeNotebooksInFolder(folderId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Long): NotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteNotebookById(id: Long)
}