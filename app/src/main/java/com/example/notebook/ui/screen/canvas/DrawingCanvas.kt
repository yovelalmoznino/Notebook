package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStrokeStyle
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notebook.data.model.*
import com.example.notebook.data.model.Stroke as CanvasStroke // מניעת התנגשות שמות
import kotlin.math.*

@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<CanvasStroke>,
    selectedStrokes: List<CanvasStroke>,
    dragOffset: Offset,
    currentStroke: CanvasStroke?,
    lassoPath: List<Offset>,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { drawComplexStroke(it, Offset.Zero) }
            selectedStrokes.forEach { drawComplexStroke(it, dragOffset) }
            currentStroke?.let { drawComplexStroke(it, Offset.Zero) }

            if (lassoPath.size > 1) {
                val path = Path().apply {
                    moveTo(lassoPath[0].x, lassoPath[0].y)
                    lassoPath.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, Color(0xFF3b82f6), style = DrawStrokeStyle(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f))))
            }
        }

        AndroidView(
            factory = { context ->
                View(context).apply {
                    setOnTouchListener { _, event ->
                        if (activeTool == CanvasTool.IMAGE) false else {
                            onAction(event)
                            true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun DrawScope.drawComplexStroke(stroke: CanvasStroke, offset: Offset) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.5f else 1f)

    when (stroke.shapeType ?: ShapeType.FREEHAND) {
        ShapeType.FREEHAND -> {
            if (stroke.isHighlighter) {
                drawHighlighter(stroke, color, offset)
            } else {
                when (stroke.penType) {
                    PenType.FOUNTAIN -> drawFountainPen(stroke, color, offset)
                    PenType.CALLIGRAPHY -> drawCalligraphy(stroke, color, offset)
                    else -> drawBallpointPen(stroke, color, offset)
                }
            }
        }
        else -> drawShape(stroke, color, offset)
    }
}

private fun DrawScope.drawBallpointPen(stroke: CanvasStroke, color: Color, offset: Offset) {
    val path = Path().apply {
        moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
        stroke.points.drop(1).forEach { lineTo(it.x + offset.x, it.y + offset.y) }
    }
    drawPath(path, color, style = DrawStrokeStyle(stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawFountainPen(stroke: CanvasStroke, color: Color, offset: Offset) {
    for (i in 0 until stroke.points.size - 1) {
        val p1 = stroke.points[i]; val p2 = stroke.points[i+1]
        val dynamicWidth = stroke.strokeWidth * (p1.pressure * 2f).coerceIn(0.5f, 2.5f)
        drawLine(color, Offset(p1.x + offset.x, p1.y + offset.y), Offset(p2.x + offset.x, p2.y + offset.y), dynamicWidth, StrokeCap.Round)
    }
}

private fun DrawScope.drawCalligraphy(stroke: CanvasStroke, color: Color, offset: Offset) {
    val angle = PI / 4
    stroke.points.forEach { pt ->
        val xS = cos(angle).toFloat() * stroke.strokeWidth; val yS = sin(angle).toFloat() * stroke.strokeWidth
        drawLine(color, Offset(pt.x + offset.x - xS, pt.y + offset.y - yS), Offset(pt.x + offset.x + xS, pt.y + offset.y + yS), 2f)
    }
}

private fun DrawScope.drawHighlighter(stroke: CanvasStroke, color: Color, offset: Offset) {
    val cap = if (stroke.markerShape == MarkerShape.SQUARE) StrokeCap.Square else StrokeCap.Round
    val path = Path().apply {
        moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
        stroke.points.drop(1).forEach { lineTo(it.x + offset.x, it.y + offset.y) }
    }
    drawPath(path, color, style = DrawStrokeStyle(stroke.strokeWidth, cap = cap, join = StrokeJoin.Bevel), blendMode = BlendMode.Multiply)
}

private fun DrawScope.drawShape(stroke: CanvasStroke, color: Color, offset: Offset) {
    if (stroke.points.size < 2) return
    val p1 = stroke.points.first(); val p2 = stroke.points.last()
    val start = Offset(p1.x + offset.x, p1.y + offset.y); val end = Offset(p2.x + offset.x, p2.y + offset.y)
    val style = DrawStrokeStyle(stroke.strokeWidth, join = StrokeJoin.Round)

    when (stroke.shapeType) {
        ShapeType.LINE -> drawLine(color, start, end, stroke.strokeWidth, StrokeCap.Round)
        ShapeType.RECTANGLE -> drawRect(color, Offset(min(start.x, end.x), min(start.y, end.y)), Size(abs(end.x - start.x), abs(end.y - start.y)), style = style)
        ShapeType.CIRCLE -> drawCircle(color, hypot(end.x - start.x, end.y - start.y), start, style = style)
        ShapeType.TRIANGLE -> {
            val path = Path().apply { moveTo(start.x + (end.x - start.x) / 2, start.y); lineTo(start.x, end.y); lineTo(end.x, end.y); close() }
            drawPath(path, color, style = style)
        }
        ShapeType.ARROW -> {
            val a = atan2(end.y - start.y, end.x - start.x); drawLine(color, start, end, stroke.strokeWidth, StrokeCap.Round)
            drawLine(color, end, Offset(end.x - 30f * cos(a - 0.5f).toFloat(), end.y - 30f * sin(a - 0.5f).toFloat()), stroke.strokeWidth)
            drawLine(color, end, Offset(end.x - 30f * cos(a + 0.5f).toFloat(), end.y - 30f * sin(a + 0.5f).toFloat()), stroke.strokeWidth)
        }
        ShapeType.STAR -> {
            val path = Path(); val centerX = start.x; val centerY = start.y
            val outer = hypot(end.x - start.x, end.y - start.y); val inner = outer / 2.5f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner; val ang = i * PI / 5 - PI / 2
                val px = centerX + r * cos(ang).toFloat(); val py = centerY + r * sin(ang).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); drawPath(path, color, style = style)
        }
        else -> {}
    }
}