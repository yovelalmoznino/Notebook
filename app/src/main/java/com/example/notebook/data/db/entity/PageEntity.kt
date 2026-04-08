package com.example.notebook.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val pageNumber: Int,
    val strokeDataJson: String = "[]",
    val imageDataJson: String = "[]", // עמודה חדשה לתמונות
    val backgroundType: String = "PLAIN",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)