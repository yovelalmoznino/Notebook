package com.example.notebook.data.model

import com.example.notebook.data.db.entity.PageEntity

// הגדרת כלי הציור (חיוני לפתרון רוב השגיאות)
enum class CanvasTool { PEN, HIGHLIGHTER, ERASER, SHAPE, LASSO, IMAGE }

enum class PageBackground { PLAIN, LINES, GRID, DOTS }

enum class ShapeType { FREEHAND, LINE, RECTANGLE, CIRCLE, TRIANGLE, ARROW, STAR }

enum class PenType { BALLPOINT, FOUNTAIN, CALLIGRAPHY }

enum class MarkerShape { ROUND, SQUARE }

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float
)

data class Stroke(
    val id: String = "",
    val points: List<StrokePoint>,
    val color: Int,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val shapeType: ShapeType? = ShapeType.FREEHAND,
    val penType: PenType = PenType.BALLPOINT,
    val markerShape: MarkerShape = MarkerShape.ROUND
)

data class CanvasImage(
    val id: String,
    val uri: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

// המודל שמחבר בין הדף מהדאטהבייס לתוכן הציור (חיוני ל-ViewModel)
data class PageUiModel(
    val page: PageEntity,
    val strokes: List<Stroke>,
    val images: List<CanvasImage>,
    val background: PageBackground
)