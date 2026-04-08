package com.example.notebook.data.model

data class Folder(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val colorHex: String,
    val updatedAt: Long
)

data class Notebook(
    val id: Long,
    val folderId: Long,
    val title: String,
    val coverColorStart: String,
    val coverColorEnd: String,
    val updatedAt: Long,
    val strokeDataJson: String = "[]" // הוספנו את זה כדי שהרפוזיטורי יוכל לגשת למידע
)

data class Stroke(
    val points: List<StrokePoint>,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)