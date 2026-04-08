package com.example.notebook.ui.screen.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.example.notebook.data.model.ShapeType
import com.example.notebook.data.model.Stroke
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<Stroke>,
    currentStroke: Stroke?,
    lassoPath: List<Offset>,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val requestDisallowInterceptTouchEvent = remember { RequestDisallowInterceptTouchEvent() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter(requestDisallowInterceptTouchEvent = requestDisallowInterceptTouchEvent) { event ->
                // קנבס שקוף כשאנחנו במצב תמונה
                if (activeTool == CanvasTool.IMAGE) return@pointerInteropFilter false

                // מעבירים את כל האירועים לפונקציה כדי שנוכל לזהות כל סוג של כפתור עט במערכת ההפעלה!
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> requestDisallowInterceptTouchEvent.invoke(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> requestDisallowInterceptTouchEvent.invoke(false)
                }

                onAction(event)
                true
            }
    ) {
        strokes.forEach { drawStrokePath(it) }
        currentStroke?.let { drawStrokePath(it) }

        // ציור הלאסו החדש: ללא מילוי פנימי בכלל, וקו מקווקו ברור
        if (lassoPath.isNotEmpty()) {
            val path = Path().apply {
                moveTo(lassoPath[0].x, lassoPath[0].y)
                lassoPath.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFF3b82f6), // כחול בהיר
                style = DrawStroke(
                    width = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f))
                )
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(stroke: Stroke) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.4f else 1f)
    val cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round
    val blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
    val style = DrawStroke(width = stroke.strokeWidth, cap = cap, join = StrokeJoin.Round)

    when (stroke.shapeType ?: ShapeType.FREEHAND) {
        ShapeType.FREEHAND -> {
            val path = Path().apply { moveTo(stroke.points[0].x, stroke.points[0].y); stroke.points.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path = path, color = color, style = style, blendMode = blendMode)
        }
        ShapeType.LINE -> { if (stroke.points.size >= 2) { drawLine(color, Offset(stroke.points.first().x, stroke.points.first().y), Offset(stroke.points.last().x, stroke.points.last().y), strokeWidth = stroke.strokeWidth, cap = cap, blendMode = blendMode) } }
        ShapeType.RECTANGLE -> { if (stroke.points.size >= 2) { val p1 = stroke.points.first(); val p2 = stroke.points.last(); val topLeft = Offset(minOf(p1.x, p2.x), minOf(p1.y, p2.y)); val rectSize = Size(abs(p2.x - p1.x), abs(p2.y - p1.y)); drawRect(color, topLeft, rectSize, style = style, blendMode = blendMode) } }
        ShapeType.CIRCLE -> { if (stroke.points.size >= 2) { val p1 = stroke.points.first(); val p2 = stroke.points.last(); val radius = hypot(p2.x - p1.x, p2.y - p1.y); drawCircle(color, radius, Offset(p1.x, p1.y), style = style, blendMode = blendMode) } }
    }
}