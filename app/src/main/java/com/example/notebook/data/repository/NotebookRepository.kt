package com.example.notebook.data.repository

import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.entity.NotebookEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val notebookDao: NotebookDao
) {
    fun getNotebooksByFolder(folderId: Long) = notebookDao.observeNotebooksInFolder(folderId)

    suspend fun insertNotebook(notebook: NotebookEntity) = notebookDao.insertNotebook(notebook)

    suspend fun getNotebookById(id: Long): NotebookEntity? = notebookDao.getNotebookById(id)

    suspend fun updateNotebook(notebook: NotebookEntity) = notebookDao.updateNotebook(notebook)

    // תיקון: מחיקה לפי ID
    suspend fun deleteNotebook(notebook: NotebookEntity) = notebookDao.deleteNotebookById(notebook.id)
}