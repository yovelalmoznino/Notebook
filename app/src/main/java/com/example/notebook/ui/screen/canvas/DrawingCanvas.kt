package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notebook.data.model.*
import com.example.notebook.data.model.Stroke as CanvasStroke
import kotlin.math.*

@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<CanvasStroke>,
    selectedStrokes: List<CanvasStroke>,
    dragOffset: Offset,
    currentStroke: CanvasStroke?,
    lassoPath: List<Offset>,
    selectionBounds: Rect?,
    onAction: (MotionEvent) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { drawComplexStroke(it, Offset.Zero) }
            selectedStrokes.forEach { drawComplexStroke(it, dragOffset) }
            currentStroke?.let { drawComplexStroke(it, Offset.Zero) }

            if (lassoPath.size > 1 && selectionBounds == null) {
                val path = Path().apply {
                    moveTo(lassoPath[0].x, lassoPath[0].y)
                    lassoPath.forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, Color(0xFF3b82f6).copy(alpha = 0.5f), style = DrawStyle(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))))
            }

            selectionBounds?.let { b ->
                drawRect(Color(0xFF3b82f6).copy(alpha = 0.15f), Offset(b.left, b.top), Size(b.width, b.height))
                drawRect(Color(0xFF3b82f6), Offset(b.left, b.top), Size(b.width, b.height), style = DrawStyle(width = 2.5f))
                drawCircle(Color.White, radius = 18f, center = Offset(b.right, b.bottom))
                drawCircle(Color(0xFF3b82f6), radius = 18f, center = Offset(b.right, b.bottom), style = DrawStyle(width = 3f))
            }
        }
        AndroidView(
            factory = { context ->
                View(context).apply {
                    setOnTouchListener { v, event ->
                        val toolType = event.getToolType(0)
                        if (toolType == MotionEvent.TOOL_TYPE_FINGER && activeTool != CanvasTool.LASSO) false
                        else {
                            v.parent.requestDisallowInterceptTouchEvent(true)
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

fun DrawScope.drawComplexStroke(stroke: CanvasStroke, offset: Offset) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.5f else 1f)

    when (stroke.shapeType ?: ShapeType.FREEHAND) {
        ShapeType.FREEHAND -> {
            if (stroke.isHighlighter) {
                val cap = if (stroke.markerShape == MarkerShape.SQUARE) StrokeCap.Square else StrokeCap.Round
                val path = Path().apply {
                    moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
                    stroke.points.forEach { lineTo(it.x + offset.x, it.y + offset.y) }
                }
                drawPath(path, color, style = DrawStyle(stroke.strokeWidth, cap = cap, join = StrokeJoin.Bevel), blendMode = BlendMode.Multiply)
            } else {
                when (stroke.penType) {
                    PenType.FOUNTAIN -> {
                        for (i in 0 until stroke.points.size - 1) {
                            val p1 = stroke.points[i]; val p2 = stroke.points[i+1]
                            val w = stroke.strokeWidth * (p1.pressure * 2f).coerceIn(0.5f, 2.5f)
                            drawLine(color, Offset(p1.x + offset.x, p1.y + offset.y), Offset(p2.x + offset.x, p2.y + offset.y), w, StrokeCap.Round)
                        }
                    }
                    PenType.CALLIGRAPHY -> {
                        val angle = PI / 4
                        stroke.points.forEach { pt ->
                            val xS = cos(angle).toFloat() * stroke.strokeWidth; val yS = sin(angle).toFloat() * stroke.strokeWidth
                            drawLine(color, Offset(pt.x + offset.x - xS, pt.y + offset.y - yS), Offset(pt.x + offset.x + xS, pt.y + offset.y + yS), 2f)
                        }
                    }
                    else -> {
                        val path = Path().apply {
                            moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
                            stroke.points.forEach { lineTo(it.x + offset.x, it.y + offset.y) }
                        }
                        drawPath(path, color, style = DrawStyle(stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        else -> drawShape(stroke, color, offset)
    }
}

private fun DrawScope.drawShape(stroke: CanvasStroke, color: Color, offset: Offset) {
    if (stroke.points.size < 2) return
    val p1 = stroke.points.first(); val p2 = stroke.points.last()
    val start = Offset(p1.x + offset.x, p1.y + offset.y); val end = Offset(p2.x + offset.x, p2.y + offset.y)
    val style = DrawStyle(stroke.strokeWidth, join = StrokeJoin.Round)
    val left = min(start.x, end.x); val top = min(start.y, end.y); val w = abs(end.x - start.x).coerceAtLeast(1f); val h = abs(end.y - start.y).coerceAtLeast(1f)
    when (stroke.shapeType) {
        ShapeType.LINE -> drawLine(color, start, end, stroke.strokeWidth, StrokeCap.Round)
        ShapeType.RECTANGLE -> drawRect(color, Offset(left, top), Size(w, h), style = style)
        ShapeType.CIRCLE -> drawOval(color, Offset(left, top), Size(w, h), style = style)
        ShapeType.TRIANGLE -> { val path = Path().apply { moveTo(left + w / 2, top); lineTo(left, top + h); lineTo(left + w, top + h); close() }; drawPath(path, color, style = style) }
        ShapeType.ARROW -> {
            val a = atan2(end.y - start.y, end.x - start.x)
            drawLine(color, start, end, stroke.strokeWidth, StrokeCap.Round)
            drawLine(color, end, Offset(end.x - 30f * cos(a - 0.5f).toFloat(), end.y - 30f * sin(a - 0.5f).toFloat()), stroke.strokeWidth)
            drawLine(color, end, Offset(end.x - 30f * cos(a + 0.5f).toFloat(), end.y - 30f * sin(a + 0.5f).toFloat()), stroke.strokeWidth)
        }
        ShapeType.STAR -> {
            val path = Path(); val cx = left + w/2; val cy = top + h/2; val outer = min(w, h)/2; val inner = outer/2.5f
            for (i in 0 until 10) { val r = if (i % 2 == 0) outer else inner; val ang = i * PI/5 - PI/2; val px = cx + r * cos(ang).toFloat(); val py = cy + r * sin(ang).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
            path.close(); drawPath(path, color, style = style)
        }
        else -> {}
    }
}