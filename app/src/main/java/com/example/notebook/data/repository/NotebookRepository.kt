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
    fun getNotebooksByFolder(folderId: Long) = notebookDao.getNotebooksByFolder(folderId)

    suspend fun insertNotebook(notebook: NotebookEntity) = notebookDao.insertNotebook(notebook)

    suspend fun deleteNotebook(notebook: NotebookEntity) = notebookDao.deleteNotebook(notebook)

    // הוסף את אלו:
    suspend fun getNotebookById(id: Long): NotebookEntity? = notebookDao.getNotebookById(id)

    suspend fun updateNotebook(notebook: NotebookEntity) = notebookDao.updateNotebook(notebook)
}