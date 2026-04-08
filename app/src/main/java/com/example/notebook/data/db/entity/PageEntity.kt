package com.example.notebook.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("notebookId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val pageNumber: Int,
    val strokeDataJson: String = "[]",
    val backgroundType: String = "PLAIN", // שומרים את שם ה-Enum כטקסט
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)