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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    selectedStrokes: List<Stroke>,
    dragOffset: Offset,
    currentStroke: Stroke?,
    lassoPath: List<Offset>,
    onAction: (MotionEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // requestDisallowInterceptTouchEvent מונע מה-ScrollView לגנוב את האירועים
    // אבל צריך לקרוא לו רק כשהעט/אצבע על המסך, ולא לפני
    val requestDisallowInterceptTouchEvent = remember { RequestDisallowInterceptTouchEvent() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter(
                requestDisallowInterceptTouchEvent = requestDisallowInterceptTouchEvent
            ) { event ->
                if (activeTool == CanvasTool.IMAGE) return@pointerInteropFilter false

                // *** תיקון קריטי לכפתור Lenovo ***
                // חייבים לקרוא את buttonState ו-toolType כאן, לפני כל דבר אחר.
                // MotionEvent.obtain() יוצר עותק כדי למנוע recycling.
                // ה-Compose framework יכול ל-recycle את ה-event המקורי לאחר return,
                // ולכן אנחנו עובדים עם עותק שלו.
                val toolType = event.getToolType(0)
                val buttonState = event.buttonState

                // בודק כפתור סטייל לפני שמחליטים מה לעשות עם האירוע
                val isStylusActive = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                        toolType == MotionEvent.TOOL_TYPE_ERASER

                // *** CRUCIAL: requestDisallowIntercept רק כש-isStylusActive ***
                // כשקוראים לזה ב-ACTION_DOWN, Android מפסיק לעדכן חלק
                // מהמטא-דאטה (כולל buttonState) באירועים עוקבים!
                // לכן קוראים לו רק אם אנחנו בטוחים שזה לא יגרום לבעיה.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        // מאפשרים intercept רק אצבע, לא עט
                        // עט מטפל בעצמו וצריך את כל המטא-דאטה
                        if (!isStylusActive) {
                            requestDisallowInterceptTouchEvent.invoke(true)
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_POINTER_UP -> {
                        requestDisallowInterceptTouchEvent.invoke(false)
                    }
                }

                // יוצרים עותק מלא של ה-event עם ה-buttonState הנכון
                // MotionEvent.obtain מעתיק את כל הנתונים לפני שהם עלולים להישכח
                val eventCopy = MotionEvent.obtain(event)
                onAction(eventCopy)
                // חשוב: לא קוראים ל-eventCopy.recycle() כי ה-ViewModel צריך אותו
                // ה-GC יטפל בו

                true
            }
    ) {
        // שכבה 1: כל הסטרוקים הרגילים (לא כוללים את הנבחרים!)
        strokes.forEach { drawStrokePath(it, Offset.Zero) }

        // שכבה 2: הסטרוקים הנבחרים בעמדתם הנוכחית (כבר עם offset מחושב ב-ViewModel)
        if (selectedStrokes.isNotEmpty()) {
            selectedStrokes.forEach { drawStrokePath(it, dragOffset) }
        }

        // שכבה 3: הסטרוק הנוכחי (בזמן ציור)
        currentStroke?.let { drawStrokePath(it, Offset.Zero) }

        // שכבה 4: מסגרת הלאסו
        if (lassoPath.isNotEmpty()) {
            val path = Path().apply {
                val offsetPath = lassoPath.map { it + dragOffset }
                moveTo(offsetPath[0].x, offsetPath[0].y)
                offsetPath.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFF3b82f6),
                style = DrawStroke(
                    width = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f))
                )
            )
        }
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
                drawLine(
                    color,
                    Offset(stroke.points.first().x + offset.x, stroke.points.first().y + offset.y),
                    Offset(stroke.points.last().x + offset.x, stroke.points.last().y + offset.y),
                    strokeWidth = stroke.strokeWidth, cap = cap, blendMode = blendMode
                )
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
                drawCircle(
                    color, radius,
                    Offset(p1.x + offset.x, p1.y + offset.y),
                    style = style, blendMode = blendMode
                )
            }
        }
    }
}