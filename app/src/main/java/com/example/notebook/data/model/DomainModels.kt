package com.example.notebook.data.model

import java.util.UUID

enum class PageBackground { PLAIN, LINES, GRID, DOTS }
enum class ShapeType { FREEHAND, LINE, RECTANGLE, CIRCLE }

data class CanvasImage(
    val id: String,
    val uri: String,
    val x: Float,
    val y: Float,
    val width: Float = 300f,
    val height: Float = 300f
)

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
    val strokeDataJson: String = "[]"
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val isHighlighter: Boolean = false,
    val shapeType: ShapeType? = ShapeType.FREEHAND
)