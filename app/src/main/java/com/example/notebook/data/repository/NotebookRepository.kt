package com.example.notebook.data.repository

import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.entity.NotebookEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val notebookDao: NotebookDao
) {
    // וודא שהשם כאן תואם ל-DAO המעודכן
    fun getNotebooksByFolder(folderId: Long) = notebookDao.getNotebooksByFolder(folderId)

    suspend fun insertNotebook(notebook: NotebookEntity) = notebookDao.insertNotebook(notebook)

    suspend fun deleteNotebook(notebook: NotebookEntity) = notebookDao.updateNotebook(notebook) // תיקון: אם זו מחיקה, כדאי להשתמש ב-deleteNotebookById

    suspend fun getNotebookById(id: Long): NotebookEntity? = notebookDao.getNotebookById(id)

    suspend fun updateNotebook(notebook: NotebookEntity) = notebookDao.updateNotebook(notebook)
}