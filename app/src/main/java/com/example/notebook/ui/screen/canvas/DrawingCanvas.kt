package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notebook.data.model.*
import com.example.notebook.data.model.Stroke as CanvasStroke

@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<CanvasStroke>,
    selectedStrokes: List<CanvasStroke>,
    dragOffset: Offset,
    currentStroke: CanvasStroke?,
    lassoPath: List<Offset>,
    onAction: (MotionEvent) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { drawComplexStroke(it, Offset.Zero) }
            currentStroke?.let { drawComplexStroke(it, Offset.Zero) }
        }
        AndroidView(
            factory = { context ->
                View(context).apply {
                    setOnTouchListener { _, event ->
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && activeTool != CanvasTool.LASSO) false
                        else { onAction(event); true }
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
    val path = Path().apply {
        moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
        stroke.points.forEach { lineTo(it.x + offset.x, it.y + offset.y) }
    }
    drawPath(path, color, style = DrawStyle(stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver)
}