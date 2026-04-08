package com.example.notebook.data.model

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