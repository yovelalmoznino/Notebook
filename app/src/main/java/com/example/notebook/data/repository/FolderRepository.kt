package com.example.notebook.data.repository

import com.example.notebook.data.db.dao.FolderDao
import com.example.notebook.data.db.entity.FolderEntity
import com.example.notebook.data.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao: FolderDao
) {
    fun observeRootFolders(): Flow<List<Folder>> =
        folderDao.observeRootFolders().map { entities -> entities.map { it.toDomain() } }

    fun observeChildFolders(parentId: Long): Flow<List<Folder>> =
        folderDao.observeChildFolders(parentId).map { entities -> entities.map { it.toDomain() } }

    suspend fun getFolderById(id: Long): Folder? = folderDao.getFolderById(id)?.toDomain()

    suspend fun getAncestors(folderId: Long): List<Folder> =
        folderDao.getAncestors(folderId).map { it.toDomain() }

    suspend fun createFolder(name: String, parentId: Long? = null, colorHex: String = "#FFD6E7"): Long =
        folderDao.insertFolder(FolderEntity(name = name, parentId = parentId, colorHex = colorHex))

    suspend fun deleteFolder(id: Long) = folderDao.deleteFolderById(id)

    private fun FolderEntity.toDomain() = Folder(id, name, parentId, colorHex, updatedAt)
}