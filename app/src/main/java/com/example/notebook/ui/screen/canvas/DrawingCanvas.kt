package com.example.notebook.ui.screen.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.*
import com.example.notebook.data.model.*
import com.example.notebook.data.model.Stroke as CanvasStroke
import kotlin.math.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    activeTool: CanvasTool,
    strokes: List<CanvasStroke>,
    selectedStrokes: List<CanvasStroke>,
    dragOffset: Offset,
    currentStroke: CanvasStroke?,
    lassoPath: List<Offset>,
    selectionBounds: Rect?,
    onDrawAction: (action: Int, x: Float, y: Float, pressure: Float, isEraser: Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // פקודת הברזל: מונעת מאנדרואיד "לגנוב" מגע בשוליים ומשחררת את כל המסך לעט
            .systemGestureExclusion()
            .pointerInput(activeTool) {
                awaitEachGesture {
                    val downEvent = awaitFirstDown(requireUnconsumed = false)
                    var isDrawing = false
                    var activePointerId: PointerId? = null

                    do {
                        // שימוש ב-Initial תופס את המגע ישירות מהחומרה לפני ששום רכיב אחר באנדרואיד חושב עליו
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val changes = event.changes

                        val allowFingerDraw = activeTool == CanvasTool.LASSO

                        // זיהוי עט (או תמיכה במצב בו זווית חדה מזוהה כ-Unknown)
                        val stylusChange = changes.firstOrNull {
                            it.type == PointerType.Stylus ||
                                    it.type == PointerType.Eraser ||
                                    it.type == PointerType.Unknown
                        }

                        val targetChange = stylusChange ?:
                        changes.firstOrNull { it.id == activePointerId } ?:
                        if (allowFingerDraw) changes.firstOrNull { it.pressed } else null

                        if (targetChange != null && (stylusChange != null || allowFingerDraw || isDrawing)) {

                            val isEraser = targetChange.type == PointerType.Eraser

                            // תיקון קריטי לזווית של הלנובו: אם החיישן מדווח 0 לחץ, נכפה עליו לפחות 0.15
                            // כדי שהקו בחיים לא יהפוך לבלתי נראה!
                            val safePressure = targetChange.pressure.coerceAtLeast(0.15f)

                            if (!isDrawing) {
                                isDrawing = true
                                activePointerId = targetChange.id
                                onDrawAction(0, targetChange.position.x, targetChange.position.y, safePressure, isEraser)
                            }

                            // בולעים את *כל* המגעים במסך כדי למנוע קפיצות מכף היד
                            changes.forEach { if (it.pressed || it.previousPressed) it.consume() }

                            // משתמשים ב-safePressure גם עבור נקודות ההיסטוריה כי אין להן מאפיין pressure משלהן
                            targetChange.historical.forEach { hist ->
                                onDrawAction(2, hist.position.x, hist.position.y, safePressure, isEraser)
                            }
                            onDrawAction(2, targetChange.position.x, targetChange.position.y, safePressure, isEraser)

                        }
                    } while (event.changes.any { it.pressed })

                    if (isDrawing) {
                        onDrawAction(1, 0f, 0f, 0f, false)
                    }
                }
            }
    ) {
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
    }
}

fun DrawScope.drawComplexStroke(stroke: CanvasStroke, offset: Offset) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.5f else 1f)

    when (stroke.shapeType ?: ShapeType.FREEHAND) {
        ShapeType.FREEHAND -> {
            if (stroke.isHighlighter) {
                val cap = if (stroke.markerShape == MarkerShape.SQUARE) StrokeCap.Square else StrokeCap.Round
                if (stroke.points.size == 1) {
                    drawCircle(color, stroke.strokeWidth / 2f, Offset(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y))
                } else {
                    val path = Path().apply {
                        moveTo(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y)
                        stroke.points.forEach { lineTo(it.x + offset.x, it.y + offset.y) }
                    }
                    drawPath(path, color, style = DrawStyle(stroke.strokeWidth, cap = cap, join = StrokeJoin.Bevel), blendMode = BlendMode.Multiply)
                }
            } else {
                when (stroke.penType) {
                    PenType.FOUNTAIN -> {
                        if (stroke.points.size == 1) {
                            // רצפת עובי מינימלית שלא יעלם
                            val w = stroke.strokeWidth * (stroke.points[0].pressure * 2f).coerceIn(0.4f, 2.5f)
                            drawCircle(color, w / 2f, Offset(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y))
                        } else if (stroke.points.size > 1) {
                            var prevP = stroke.points[0]
                            var prevW = stroke.strokeWidth * (prevP.pressure * 2f).coerceIn(0.4f, 2.5f)
                            for (i in 1 until stroke.points.size) {
                                val p = stroke.points[i]
                                val currentW = stroke.strokeWidth * (p.pressure * 2f).coerceIn(0.4f, 2.5f)
                                val smoothW = prevW + (currentW - prevW) * 0.2f
                                drawLine(color, Offset(prevP.x + offset.x, prevP.y + offset.y), Offset(p.x + offset.x, p.y + offset.y), smoothW, StrokeCap.Round)
                                prevP = p
                                prevW = smoothW
                            }
                        }
                    }
                    PenType.CALLIGRAPHY -> {
                        if (stroke.points.size == 1) {
                            drawCircle(color, stroke.strokeWidth / 2f, Offset(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y))
                        } else if (stroke.points.size > 1) {
                            val angle = PI / 4
                            val xS = (cos(angle) * stroke.strokeWidth * 0.7).toFloat()
                            val yS = (sin(angle) * stroke.strokeWidth * 0.7).toFloat()
                            val path = Path()
                            var prev = stroke.points[0]
                            for (i in 1 until stroke.points.size) {
                                val curr = stroke.points[i]
                                path.moveTo(prev.x + offset.x - xS, prev.y + offset.y + yS)
                                path.lineTo(prev.x + offset.x + xS, prev.y + offset.y - yS)
                                path.lineTo(curr.x + offset.x + xS, curr.y + offset.y - yS)
                                path.lineTo(curr.x + offset.x - xS, curr.y + offset.y + yS)
                                path.close()
                                prev = curr
                            }
                            drawPath(path, color, style = Fill)
                        }
                    }
                    else -> {
                        if (stroke.points.size == 1) {
                            drawCircle(color, stroke.strokeWidth / 2f, Offset(stroke.points[0].x + offset.x, stroke.points[0].y + offset.y))
                        } else if (stroke.points.size > 1) {
                            val path = Path()
                            var prevX = stroke.points[0].x + offset.x
                            var prevY = stroke.points[0].y + offset.y
                            path.moveTo(prevX, prevY)

                            for (i in 1 until stroke.points.size) {
                                val curX = stroke.points[i].x + offset.x
                                val curY = stroke.points[i].y + offset.y
                                val midX = (prevX + curX) / 2f
                                val midY = (prevY + curY) / 2f

                                if (i == 1) {
                                    path.lineTo(midX, midY)
                                } else {
                                    path.quadraticBezierTo(prevX, prevY, midX, midY)
                                }
                                prevX = curX
                                prevY = curY
                            }
                            path.lineTo(prevX, prevY)
                            drawPath(path, color, style = DrawStyle(stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
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