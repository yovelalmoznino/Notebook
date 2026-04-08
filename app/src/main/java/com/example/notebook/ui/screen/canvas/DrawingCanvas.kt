package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawScopeStroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.example.notebook.data.model.*
import kotlin.math.hypot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<Stroke>,
    selectedStrokes: List<Stroke>,
    dragOffset: Offset,
    currentStroke: Stroke?,
    lassoPath: List<Offset>,
    onAction: (MotionEvent) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                val toolType = event.getToolType(0)

                // התיקון הקריטי: אם זו אצבע (וזה לא כלי הלאסו), אל תצרוך את האירוע
                // זה יאפשר ל-LazyColumn לזהות את הגלילה כרגיל
                if (toolType == MotionEvent.TOOL_TYPE_FINGER && activeTool != CanvasTool.LASSO) {
                    return@pointerInteropFilter false
                }

                // אם זה עט או מחק חומרה, תעביר לציור ותחזיר true (אל תגלול)
                onAction(event)
                true
            }
    ) {
        strokes.forEach { drawStroke(it) }
        selectedStrokes.forEach { drawStroke(it) }
        currentStroke?.let { drawStroke(it) }

        if (lassoPath.size > 1) {
            val path = Path().apply {
                moveTo(lassoPath[0].x, lassoPath[0].y)
                lassoPath.forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path = path,
                color = Color.Blue.copy(alpha = 0.3f),
                style = DrawScopeStroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }
    }
}

private fun DrawScope.drawStroke(stroke: Stroke) {
    if (stroke.points.isEmpty()) return

    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x, first.y)

        if (stroke.shapeType == ShapeType.FREEHAND || stroke.shapeType == null) {
            stroke.points.forEach { pt -> this.lineTo(pt.x, pt.y) }
        } else {
            val last = stroke.points.last()
            when (stroke.shapeType) {
                ShapeType.LINE -> lineTo(last.x, last.y)
                ShapeType.RECTANGLE -> addRect(androidx.compose.ui.geometry.Rect(first.x, first.y, last.x, last.y))
                ShapeType.CIRCLE -> {
                    val radius = hypot(last.x - first.x, last.y - first.y)
                    addOval(androidx.compose.ui.geometry.Rect(first.x - radius, first.y - radius, first.x + radius, first.y + radius))
                }
                else -> stroke.points.forEach { pt -> this.lineTo(pt.x, pt.y) }
            }
        }
    }

    // בחירת סוג ה"שפיץ" לפי הגדרות המרקר/עט
    val cap = if (stroke.markerShape == MarkerShape.SQUARE) StrokeCap.Square else StrokeCap.Round
    val join = if (stroke.markerShape == MarkerShape.SQUARE) StrokeJoin.Miter else StrokeJoin.Round

    drawPath(
        path = path,
        color = Color(stroke.color),
        style = DrawScopeStroke(
            width = stroke.strokeWidth,
            cap = cap,
            join = join
        ),
        blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
    )
}