package com.example.notebook.ui.screen.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
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
            .pointerInput(activeTool) {
                awaitPointerEventScope {
                    var activePenId: PointerId? = null
                    var lastStylusTime = 0L

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointers = event.changes

                        // 1. האם המערכת רואה עט אמיתי (אפילו רק מרחף באוויר מעל המסך)?
                        val realStylus = pointers.firstOrNull {
                            it.type == PointerType.Stylus || it.type == PointerType.Eraser
                        }

                        if (realStylus != null) {
                            lastStylusTime = System.currentTimeMillis() // שומרים את הרגע האחרון שבו זיהינו נוכחות של העט
                        }

                        // אם ראינו עט ב-800 המילישניות האחרונות, אנחנו מניחים שהמשתמש במצב כתיבה פעיל
                        val isWritingMode = (System.currentTimeMillis() - lastStylusTime) < 800L

                        // 2. החלפה חכמה לעט האמיתי (מתקן בעיות של מגע כף יד או זיהוי איטי במסכי מחשב)
                        if (activePenId != null && realStylus != null && realStylus.id != activePenId && realStylus.pressed) {
                            // המערכת בדיוק קלטה שזה עט אמיתי בזמן שאנחנו עוקבים אחרי מגע שהתחיל כ"אצבע" שגויה - נחליף לעט!
                            onDrawAction(1, 0f, 0f, 0f, false)
                            activePenId = realStylus.id
                            onDrawAction(0, realStylus.position.x, realStylus.position.y, realStylus.pressure, realStylus.type == PointerType.Eraser)
                        }

                        // 3. איתור המגע שאחריו אנחנו צריכים לעקוב כדי לצייר
                        val targetPointer = pointers.firstOrNull { it.id == activePenId }
                            ?: realStylus?.takeIf { it.pressed }
                            ?: pointers.firstOrNull { activeTool == CanvasTool.LASSO && it.pressed }
                            // הקסם: אם אנחנו במצב כתיבה ויש מכה מהירה במסך, נניח שזה העט ונתפוס את המגע לפני שהגלילה תגנוב אותו
                            ?: pointers.firstOrNull { isWritingMode && it.pressed }

                        if (targetPointer != null) {
                            // משתיקים את כל המגעים באופן אגרסיבי כדי למנוע מגלילת העמוד לקפוץ
                            pointers.forEach { if (it.pressed || it.previousPressed) it.consume() }

                            val isEraser = targetPointer.type == PointerType.Eraser

                            if (targetPointer.pressed) {
                                if (activePenId != targetPointer.id) {
                                    // התחלת ציור של קו חדש
                                    activePenId = targetPointer.id
                                    onDrawAction(0, targetPointer.position.x, targetPointer.position.y, targetPointer.pressure, isEraser)
                                } else {
                                    // תנועה של הקו - ציור של כל הנקודות ההיסטוריות שהמערכת "ארזה" (קריטי לקשקוש מהיר)
                                    targetPointer.historical.forEach { hist ->
                                        onDrawAction(2, hist.position.x, hist.position.y, targetPointer.pressure, isEraser)
                                    }
                                    onDrawAction(2, targetPointer.position.x, targetPointer.position.y, targetPointer.pressure, isEraser)
                                }
                            } else if (!targetPointer.pressed && activePenId == targetPointer.id) {
                                // המשתמש הרים את העט מהמסך - סיום הקו
                                onDrawAction(1, targetPointer.position.x, targetPointer.position.y, targetPointer.pressure, isEraser)
                                activePenId = null
                            }
                        } else if (activePenId != null) {
                            // איבוד פתאומי של מגע
                            onDrawAction(1, 0f, 0f, 0f, false)
                            activePenId = null
                        }
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