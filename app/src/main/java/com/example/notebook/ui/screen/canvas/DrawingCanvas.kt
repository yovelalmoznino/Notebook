package com.example.notebook.ui.screen.canvas

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.viewinterop.AndroidView
import com.example.notebook.data.model.ShapeType
import com.example.notebook.data.model.Stroke
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<Stroke>,
    selectedStrokes: List<Stroke>,
    dragOffset: Offset,
    currentStroke: Stroke?,
    lassoPath: List<Offset>,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // 1. שכבת הציור הויזואלית (כאן אנחנו מציירים הכל)
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { drawStrokePath(it, Offset.Zero) }
            selectedStrokes.forEach { drawStrokePath(it, dragOffset) }
            currentStroke?.let { drawStrokePath(it, Offset.Zero) }

            if (lassoPath.size > 1) {
                val path = Path().apply {
                    moveTo(lassoPath[0].x, lassoPath[0].y)
                    lassoPath.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(
                    path = path,
                    color = Color(0xFF3b82f6),
                    style = DrawStroke(
                        width = 8f, // שימוש בפיקסלים ישירים כדי למנוע בעיות של dp
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f))
                    )
                )
            }
        }

        // 2. שכבת המגע הגולמית - קולטת את הלחיצות של עט הלנובו בלי ש-Compose יסנן אותן
        AndroidView(
            factory = { context ->
                View(context).apply {
                    setOnTouchListener { _, event ->
                        if (activeTool == CanvasTool.IMAGE) {
                            false // נותן לתמונות שמתחת להגיב לגרירה
                        } else {
                            val toolType = event.getToolType(0)
                            val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
                            val isLassoWithFinger = toolType == MotionEvent.TOOL_TYPE_FINGER && activeTool == CanvasTool.LASSO

                            if (isStylus || isLassoWithFinger) {
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                        parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                                        parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                onAction(event)
                                true
                            } else {
                                false // אצבע במצב רגיל תאפשר גלילה
                            }
                        }
                    }

                    // תמיכה בריחוף (Hover) וכפתורים
                    setOnGenericMotionListener { _, event ->
                        if (activeTool != CanvasTool.IMAGE &&
                            (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
                                    event.actionMasked == MotionEvent.ACTION_HOVER_ENTER)) {
                            onAction(event)
                            true
                        } else {
                            false
                        }
                    }

                    // אופטימיזציה לזמן תגובה אפסי (Latency)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_NONE) // תיקון השגיאה מהתמונה
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun DrawScope.drawStrokePath(stroke: Stroke, offset: Offset) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.4f else 1f)
    val cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round
    val blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
    val style = DrawStroke(width = stroke.strokeWidth, cap = cap, join = StrokeJoin.Round)

    when (stroke.shapeType ?: ShapeType.FREEHAND) {
        ShapeType.FREEHAND -> {
            val path = Path().apply {
                moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
                stroke.points.drop(1).forEach { lineTo(it.x + offset.x, it.y + offset.y) }
            }
            drawPath(path = path, color = color, style = style, blendMode = blendMode)
        }
        ShapeType.LINE -> {
            if (stroke.points.size >= 2) {
                drawLine(color, Offset(stroke.points.first().x + offset.x, stroke.points.first().y + offset.y), Offset(stroke.points.last().x + offset.x, stroke.points.last().y + offset.y), strokeWidth = stroke.strokeWidth, cap = cap, blendMode = blendMode)
            }
        }
        ShapeType.RECTANGLE -> {
            if (stroke.points.size >= 2) {
                val p1 = stroke.points.first(); val p2 = stroke.points.last()
                val topLeft = Offset(minOf(p1.x, p2.x) + offset.x, minOf(p1.y, p2.y) + offset.y)
                val rectSize = Size(abs(p2.x - p1.x), abs(p2.y - p1.y))
                drawRect(color, topLeft, rectSize, style = style, blendMode = blendMode)
            }
        }
        ShapeType.CIRCLE -> {
            if (stroke.points.size >= 2) {
                val p1 = stroke.points.first(); val p2 = stroke.points.last()
                val radius = hypot(p2.x - p1.x, p2.y - p1.y)
                drawCircle(color, radius, Offset(p1.x + offset.x, p1.y + offset.y), style = style, blendMode = blendMode)
            }
        }
    }
}