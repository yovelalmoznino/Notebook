package com.example.notebook.data.repository

import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.entity.NotebookEntity
import com.example.notebook.data.model.Notebook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val notebookDao: NotebookDao
) {
    fun observeNotebooksInFolder(folderId: Long): Flow<List<Notebook>> =
        notebookDao.observeNotebooksInFolder(folderId).map { it.map { entity -> entity.toDomain() } }

    suspend fun getNotebookById(id: Long): Notebook? = notebookDao.getNotebookById(id)?.toDomain()

    suspend fun createNotebook(folderId: Long, title: String): Long =
        notebookDao.insertNotebook(NotebookEntity(folderId = folderId, title = title))

    suspend fun saveStrokes(notebookId: Long, strokeJson: String) =
        notebookDao.saveStrokes(notebookId, strokeJson)

    suspend fun deleteNotebook(id: Long) = notebookDao.deleteNotebookById(id)

    private fun NotebookEntity.toDomain() = Notebook(
        id, folderId, title, coverColorStart, coverColorEnd, updatedAt, strokeDataJson
    )
}