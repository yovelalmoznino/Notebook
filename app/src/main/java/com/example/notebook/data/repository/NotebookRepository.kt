package com.example.notebook.data.repository

import com.example.notebook.data.db.dao.NotebookDao
import com.example.notebook.data.db.entity.NotebookEntity
import javax.inject.Inject
import javax.inject.Singleton
import com.example.notebook.data.db.dao.PageDao
import com.example.notebook.data.db.entity.PageEntity

@Singleton
class NotebookRepository @Inject constructor(
    private val notebookDao: NotebookDao,
    private val pageDao: PageDao
) {
    fun getNotebooksByFolder(folderId: Long) = notebookDao.observeNotebooksInFolder(folderId)

    suspend fun insertNotebook(notebook: NotebookEntity) = notebookDao.insertNotebook(notebook)

    suspend fun getNotebookById(id: Long): NotebookEntity? = notebookDao.getNotebookById(id)

    suspend fun updateNotebook(notebook: NotebookEntity) = notebookDao.updateNotebook(notebook)

    // תיקון: מחיקה לפי ID
    suspend fun deleteNotebook(notebook: NotebookEntity) = notebookDao.deleteNotebookById(notebook.id)
    suspend fun createNotebook(folderId: Long, title: String) {
        val newNotebook = NotebookEntity(
            folderId = folderId,
            title = title
        )
        insertNotebook(newNotebook)
    }
    fun observePages(notebookId: Long) = pageDao.observePagesForNotebook(notebookId)

    suspend fun getPages(notebookId: Long) = pageDao.getPagesForNotebook(notebookId)

    suspend fun insertPage(page: PageEntity) = pageDao.insertPage(page)

    suspend fun updatePage(page: PageEntity) = pageDao.updatePage(page)

    // פונקציית עזר ליצירת דף ראשון אם המחברת ריקה
    suspend fun ensureFirstPageExists(notebookId: Long) {
        val pages = pageDao.getPagesForNotebook(notebookId)
        if (pages.isEmpty()) {
            pageDao.insertPage(PageEntity(notebookId = notebookId, pageNumber = 1))
        }
    }
}